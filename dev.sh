#!/usr/bin/env bash
# ============================================================
# lab_reserve 开发环境一键脚本
#
#   ./dev.sh start              # docker 起 MySQL+Redis → 后端 mvnw spring-boot:run → 前端 npm run dev
#   ./dev.sh stop               # 停后端 + 前端（数据库容器保留）
#   ./dev.sh restart            # 重启后端 + 前端
#   ./dev.sh down               # 停后端 + 前端 + 数据库容器
#   ./dev.sh status             # 查看后端/前端/数据库状态
#   ./dev.sh logs [backend|frontend|db]   # 跟踪日志（默认 backend）
#
# 约定（见根目录 AGENTS.md §4）：数据库走 Docker，后端走 Maven，前端走 npm。
# 不用 Docker 跑后端/前端，因为每次都要重建镜像，太慢。
# ============================================================
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

LOG_DIR="$ROOT_DIR/logs"
RUN_DIR="$ROOT_DIR/.dev"
BACKEND_LOG="$LOG_DIR/dev-backend.log"
FRONTEND_LOG="$LOG_DIR/dev-frontend.log"
BACKEND_PID_FILE="$RUN_DIR/backend.pid"
FRONTEND_PID_FILE="$RUN_DIR/frontend.pid"

MYSQL_CONTAINER="lab-reserve-mysql"
REDIS_CONTAINER="lab-reserve-redis"

# 注意：不要叫 FRONTEND_PORT —— .env 里也有同名变量（nginx 容器的 80），
# source .env 会把它覆盖掉，所以这里用 DEV_ 前缀区分。
DEV_FRONTEND_PORT=5173
HEALTH_URL="http://localhost:8080/api/system/health"

# ---------- 输出 ----------
info()  { printf '\033[36m[dev]\033[0m %s\n' "$*"; }
ok()    { printf '\033[32m[ ok]\033[0m %s\n' "$*"; }
warn()  { printf '\033[33m[warn]\033[0m %s\n' "$*"; }
err()   { printf '\033[31m[fail]\033[0m %s\n' "$*" >&2; }

# ---------- 基础 ----------
load_env() {
    if [[ ! -f "$ROOT_DIR/.env" ]]; then
        err "缺少 .env：请先 cp .env.example .env 并填好数据库/Redis/JWT 凭据"
        exit 1
    fi
    # .env 里的键值直接导出给后端进程（Spring Boot 读环境变量）
    set -a
    # shellcheck disable=SC1091
    source "$ROOT_DIR/.env"
    set +a
    BACKEND_PORT="${BACKEND_PORT:-8080}"
    HEALTH_URL="http://localhost:${BACKEND_PORT}/api/system/health"
    mkdir -p "$LOG_DIR" "$RUN_DIR"
}

pid_of() {  # $1 = pid 文件；输出 pid，进程不存在则输出空
    local f="$1"
    [[ -f "$f" ]] || return 0
    local pid
    pid="$(cat "$f" 2>/dev/null || true)"
    [[ -n "$pid" ]] || return 0
    if kill -0 "$pid" 2>/dev/null; then
        echo "$pid"
    fi
}

kill_pid_file() {  # $1 = pid 文件  $2 = 名称
    local f="$1" name="$2" pid
    pid="$(pid_of "$f")"
    if [[ -z "$pid" ]]; then
        warn "$name 未在运行"
        rm -f "$f"
        return 0
    fi
    info "停止 ${name}（pid=${pid}）…"
    # 先杀子进程再杀父进程，避免 mvnw/vite 留下孤儿进程
    pkill -P "$pid" 2>/dev/null || true
    kill "$pid" 2>/dev/null || true
    for _ in $(seq 1 20); do
        kill -0 "$pid" 2>/dev/null || break
        sleep 0.5
    done
    if kill -0 "$pid" 2>/dev/null; then
        warn "$name 未在 10s 内退出，强制 kill -9"
        pkill -9 -P "$pid" 2>/dev/null || true
        kill -9 "$pid" 2>/dev/null || true
    fi
    rm -f "$f"
    ok "$name 已停止"
}

# ---------- 数据库 ----------
port_in_use() { # $1 = 端口
    lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

start_db() {
    info "启动 MySQL + Redis 容器…"
    docker compose up -d mysql redis >/dev/null
    for _ in $(seq 1 40); do
        local mysql redis
        mysql="$(docker inspect -f '{{.State.Health.Status}}' "$MYSQL_CONTAINER" 2>/dev/null || echo missing)"
        redis="$(docker inspect -f '{{.State.Health.Status}}' "$REDIS_CONTAINER" 2>/dev/null || echo missing)"
        if [[ "$mysql" == "healthy" && "$redis" == "healthy" ]]; then
            ok "MySQL / Redis 均已 healthy"
            return 0
        fi
        sleep 2
    done
    err "数据库容器未在 80s 内 healthy，请执行：docker compose logs mysql"
    return 1
}

# ---------- 后端 ----------
wait_backend_healthy() {
    info "等待后端就绪（${HEALTH_URL}）…"
    for _ in $(seq 1 60); do
        if curl -fsS -X POST "$HEALTH_URL" >/dev/null 2>&1; then
            ok "后端已就绪：http://localhost:${BACKEND_PORT}（接口文档 /doc.html）"
            return 0
        fi
        sleep 2
    done
    warn "后端 120s 内未通过健康检查，看看日志：./dev.sh logs backend"
    return 1
}

start_backend() {
    if [[ -n "$(pid_of "$BACKEND_PID_FILE")" ]]; then
        warn "后端已在运行（pid=$(pid_of "$BACKEND_PID_FILE")），跳过"
        return 0
    fi
    if port_in_use "$BACKEND_PORT"; then
        err "端口 ${BACKEND_PORT} 已被占用（可能是另一个后端或 Docker 里的 backend）。请先停掉它。"
        return 1
    fi
    info "启动后端：./mvnw spring-boot:run（日志 → logs/dev-backend.log）"
    (
        cd "$ROOT_DIR/backend" || exit 1
        nohup ./mvnw -q -DskipTests spring-boot:run >"$BACKEND_LOG" 2>&1 &
        echo $! >"$BACKEND_PID_FILE"
    )
    wait_backend_healthy
}

# ---------- 前端 ----------
start_frontend() {
    if [[ -n "$(pid_of "$FRONTEND_PID_FILE")" ]]; then
        warn "前端已在运行（pid=$(pid_of "$FRONTEND_PID_FILE")），跳过"
        return 0
    fi
    if port_in_use "$DEV_FRONTEND_PORT"; then
        err "端口 ${DEV_FRONTEND_PORT} 已被占用，请先停掉占用进程。"
        return 1
    fi
    if [[ ! -d "$ROOT_DIR/frontend/node_modules" ]]; then
        info "首次运行，安装前端依赖（npm install）…"
        (cd "$ROOT_DIR/frontend" && npm install --no-audit --no-fund)
    fi
    info "启动前端：npm run dev（日志 → logs/dev-frontend.log）"
    (
        cd "$ROOT_DIR/frontend" || exit 1
        nohup npm run dev >"$FRONTEND_LOG" 2>&1 &
        echo $! >"$FRONTEND_PID_FILE"
    )
    for _ in $(seq 1 30); do
        if curl -fsS "http://localhost:${DEV_FRONTEND_PORT}/" >/dev/null 2>&1; then
            ok "前端已就绪：http://localhost:${DEV_FRONTEND_PORT}"
            return 0
        fi
        sleep 1
    done
    warn "前端未就绪，看看日志：./dev.sh logs frontend"
    return 1
}

# ---------- 命令 ----------
cmd_start() {
    load_env
    start_db || exit 1
    start_backend
    start_frontend
    echo
    ok "开发环境已启动"
    echo "  前端      http://localhost:${DEV_FRONTEND_PORT}"
    echo "  后端      http://localhost:${BACKEND_PORT}"
    echo "  接口文档  http://localhost:${BACKEND_PORT}/doc.html"
    echo "  停止      ./dev.sh stop    （连库一起停：./dev.sh down）"
}

cmd_stop() {
    load_env
    kill_pid_file "$FRONTEND_PID_FILE" "前端"
    kill_pid_file "$BACKEND_PID_FILE" "后端"
    ok "已停止（数据库容器仍在运行，需要一并停止用 ./dev.sh down）"
}

cmd_restart() {
    cmd_stop
    cmd_start
}

cmd_down() {
    cmd_stop
    info "停止数据库容器…"
    docker compose stop mysql redis >/dev/null 2>&1 || true
    ok "数据库容器已停止（数据卷保留；彻底重置用 docker compose down -v）"
}

cmd_status() {
    load_env
    local pid
    pid="$(pid_of "$BACKEND_PID_FILE")"
    if [[ -n "$pid" ]]; then
        if curl -fsS -X POST "$HEALTH_URL" >/dev/null 2>&1; then
            ok "后端：运行中（pid=${pid}，健康检查通过）"
        else
            warn "后端：运行中（pid=${pid}，但健康检查未通过）"
        fi
    else
        err "后端：未运行"
    fi

    pid="$(pid_of "$FRONTEND_PID_FILE")"
    if [[ -n "$pid" ]]; then
        ok "前端：运行中（pid=${pid}，http://localhost:${DEV_FRONTEND_PORT}）"
    else
        err "前端：未运行"
    fi

    for c in "$MYSQL_CONTAINER" "$REDIS_CONTAINER"; do
        local st
        st="$(docker inspect -f '{{.State.Health.Status}}' "$c" 2>/dev/null || echo missing)"
        if [[ "$st" == "healthy" ]]; then ok "${c}：${st}"; else err "${c}：${st}"; fi
    done
    echo
    echo "日志：logs/dev-backend.log · logs/dev-frontend.log"
}

cmd_logs() {
    load_env
    case "${1:-backend}" in
        backend)  tail -n 200 -f "$BACKEND_LOG" ;;
        frontend) tail -n 200 -f "$FRONTEND_LOG" ;;
        db)       docker compose logs -f --tail 200 mysql redis ;;
        *)        err "用法：./dev.sh logs [backend|frontend|db]"; exit 1 ;;
    esac
}

usage() {
    sed -n '3,15p' "$0" | sed 's/^# \{0,1\}//'
}

case "${1:-}" in
    start)   cmd_start ;;
    stop)    cmd_stop ;;
    restart) cmd_restart ;;
    down)    cmd_down ;;
    status)  cmd_status ;;
    logs)    shift; cmd_logs "${1:-backend}" ;;
    ""|-h|--help|help) usage ;;
    *) err "未知命令：$1"; usage; exit 1 ;;
esac
