#!/usr/bin/env python3
"""本地调试 SMTP 服务器（不依赖第三方库）。

用途：在没有 QQ 邮箱授权码时联调邮件投递链路。
JavaMail（spring-boot-starter-mail）连上来之后，本脚本把每封邮件存成 .eml 文件，
并在 stdout 打印收件人 / 主题，供验收脚本断言「邮件真的发出去了」。

启动：

    python3 scripts/tools/dev_smtp.py --port 1025 --out /tmp/dev_smtp

后端对应配置（环境变量，或用 ./dev.sh 之前 export）：

    MAIL_HOST=127.0.0.1 MAIL_PORT=1025 MAIL_SMTP_AUTH=false MAIL_SMTP_SSL=false

支持的命令：EHLO / HELO、MAIL FROM、RCPT TO、DATA、RSET、NOOP、QUIT。
不支持 AUTH / STARTTLS（因此后端必须把 auth 与 ssl 关掉）。
"""

import argparse
import os
import socket
import socketserver
import threading
import time
import itertools

_message_seq = itertools.count(1)
_lock = threading.Lock()


class SmtpHandler(socketserver.StreamRequestHandler):
    """极简 SMTP 应答器：够 JavaMail 用即可。"""

    def reply(self, text: str) -> None:
        self.wfile.write((text + "\r\n").encode("utf-8"))
        self.wfile.flush()

    def _readline(self) -> str:
        line = self.rfile.readline()
        if not line:
            raise ConnectionError("client closed")
        return line.decode("utf-8", errors="replace").rstrip("\r\n")

    def handle(self) -> None:  # noqa: C901 - 状态机本来就分支多
        self.reply("220 dev-smtp ready")
        sender = ""
        recipients: list[str] = []
        try:
            while True:
                line = self._readline()
                upper = line.upper()
                if upper.startswith("EHLO") or upper.startswith("HELO"):
                    # 单行应答即可；不宣告 AUTH / STARTTLS，JavaMail 就不会尝试
                    self.reply("250 dev-smtp")
                elif upper.startswith("MAIL FROM:"):
                    sender = line[10:].strip().strip("<>")
                    recipients = []
                    self.reply("250 OK")
                elif upper.startswith("RCPT TO:"):
                    recipients.append(line[8:].strip().strip("<>"))
                    self.reply("250 OK")
                elif upper == "DATA":
                    self.reply("354 End data with <CR><LF>.<CR><LF>")
                    body_lines = []
                    while True:
                        data_line = self._readline()
                        if data_line == ".":
                            break
                        body_lines.append(data_line)
                    self._store(sender, recipients, "\r\n".join(body_lines))
                    self.reply("250 OK queued")
                elif upper == "RSET":
                    sender, recipients = "", []
                    self.reply("250 OK")
                elif upper == "NOOP":
                    self.reply("250 OK")
                elif upper == "QUIT":
                    self.reply("221 Bye")
                    return
                elif upper.startswith("STARTTLS") or upper.startswith("AUTH"):
                    self.reply("502 Command not implemented")
                else:
                    self.reply("250 OK")
        except (ConnectionError, OSError):
            return

    def _store(self, sender: str, recipients: list[str], body: str) -> None:
        with _lock:
            seq = next(_message_seq)
        stamp = time.strftime("%Y%m%d-%H%M%S")
        name = f"{stamp}-{seq:03d}.eml"
        path = os.path.join(self.server.out_dir, name)
        header = f"X-Dev-Smtp-From: {sender}\r\nX-Dev-Smtp-To: {', '.join(recipients)}\r\n"
        with open(path, "w", encoding="utf-8") as fp:
            fp.write(header + body + "\r\n")
        subject = ""
        for body_line in body.split("\r\n"):
            if body_line.lower().startswith("subject:"):
                subject = body_line[8:].strip()
                break
        print(f"[dev-smtp] 收到邮件 -> {path} | to={','.join(recipients)} | subject={subject}", flush=True)


class SmtpServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, address, handler, out_dir: str):
        super().__init__(address, handler)
        self.out_dir = out_dir


def main() -> None:
    parser = argparse.ArgumentParser(description="本地调试 SMTP 服务器")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=1025)
    parser.add_argument("--out", default="/tmp/dev_smtp", help="邮件落盘目录")
    args = parser.parse_args()

    os.makedirs(args.out, exist_ok=True)
    server = SmtpServer((args.host, args.port), SmtpHandler, args.out)
    print(f"[dev-smtp] listening on {args.host}:{args.port}, 邮件保存到 {args.out}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[dev-smtp] bye", flush=True)
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
