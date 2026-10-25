<template>
  <div class="login-page">
    <div class="login-panel">
      <div class="login-brand">
        <span class="login-mark">
          <el-icon :size="22" color="#fff"><School /></el-icon>
        </span>
        <h1>实验室预约管理系统</h1>
        <p>Laboratory Resource Reservation &amp; Maintenance System</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        label-position="top"
        @keyup.enter="handleSubmit"
      >
        <el-form-item prop="username">
          <el-input
            v-model.trim="form.username"
            placeholder="请输入账号（学号 / 工号 / 管理员账号）"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" class="login-submit" :loading="loading" @click="handleSubmit">
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-tip">
        <p>演示账号：<b>admin</b>、<b>labadmin</b>、<b>repair01</b>、<b>stu2021001</b>、<b>tea1001</b> 等</p>
        <p>全部演示账号密码均为 <b>123456</b>（含系统管理员）</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { isApiError } from '@/utils/request'
import type { LoginParams } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive<LoginParams>({
  username: '',
  password: ''
})

const rules: FormRules<LoginParams> = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ]
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    await router.replace(redirect)
  } catch (error) {
    const message = isApiError(error) ? error.message : '登录失败，请稍后重试'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  overflow: hidden;
  background: linear-gradient(150deg, #f7f4f1 0%, #f0ede9 45%, #e9e3dc 100%);
}

/* 两团赭色光斑：营造纵深，也给玻璃卡片可透的背景 */
.login-page::before,
.login-page::after {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  content: '';
}

.login-page::before {
  top: -160px;
  left: -120px;
  width: 460px;
  height: 460px;
  background: rgb(176 93 46 / 22%);
}

.login-page::after {
  right: -180px;
  bottom: -200px;
  width: 520px;
  height: 520px;
  background: rgb(212 163 115 / 32%);
}

.login-panel {
  position: relative;
  z-index: 1;
  width: 420px;
  padding: 40px 40px 28px;
  background: var(--lr-material-thick);
  border: 1px solid rgb(255 255 255 / 70%);
  border-radius: var(--lr-radius-xl);
  box-shadow: 0 24px 64px -20px rgb(176 93 46 / 32%);
  -webkit-backdrop-filter: saturate(var(--lr-material-thick-saturate))
    blur(var(--lr-material-thick-blur));
  backdrop-filter: saturate(var(--lr-material-thick-saturate)) blur(var(--lr-material-thick-blur));
}

.login-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  margin: 0 auto 18px;
  background: var(--lr-gradient-primary);
  border-radius: var(--lr-radius-lg);
  box-shadow: var(--lr-shadow-primary);
}

.login-brand {
  margin-bottom: 28px;
  text-align: center;

  h1 {
    margin: 0 0 6px;
    font-size: 22px;
    font-weight: 600;
    color: var(--lr-text-primary);
    letter-spacing: -0.02em;
  }

  p {
    margin: 0;
    font-size: 12px;
    line-height: 1.5;
    color: var(--lr-text-secondary);
    letter-spacing: 0.3px;
  }
}

/* 登录按钮：pill 主 CTA */
.login-submit {
  width: 100%;
  height: 44px;
  font-size: var(--lr-font-lg);
  font-weight: 600;
  letter-spacing: 4px;
  border: none;
  border-radius: var(--lr-radius-pill);
  box-shadow: var(--lr-shadow-primary);

  &:hover {
    box-shadow: 0 8px 22px -6px rgb(176 93 46 / 45%);
  }
}

.login-tip {
  padding-top: 16px;
  margin-top: 8px;
  text-align: center;
  border-top: 1px dashed var(--lr-border);

  p {
    margin: 4px 0;
    font-size: 12px;
    line-height: 1.6;
    color: var(--lr-text-secondary);
  }

  b {
    font-weight: 600;
    color: var(--lr-text-regular);
  }
}

/* 不支持磨砂时降级为不透明底 */
@supports not (backdrop-filter: blur(1px)) {
  .login-panel {
    background: var(--lr-bg-raised);
    -webkit-backdrop-filter: none;
    backdrop-filter: none;
  }
}
</style>
