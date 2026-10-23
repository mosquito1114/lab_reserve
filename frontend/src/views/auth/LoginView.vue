<template>
  <div class="login-page">
    <div class="login-panel">
      <div class="login-brand">
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
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: linear-gradient(135deg, #1f2b4d 0%, #2f4f8f 45%, #4a86c8 100%);
}

.login-panel {
  width: 400px;
  padding: 36px 36px 24px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 12px 40px rgb(0 0 0 / 25%);
}

.login-brand {
  margin-bottom: 24px;
  text-align: center;

  h1 {
    margin: 0 0 6px;
    font-size: 22px;
    font-weight: 600;
    color: var(--lr-text-primary);
  }

  p {
    margin: 0;
    font-size: 12px;
    color: var(--lr-text-secondary);
    letter-spacing: 0.3px;
  }
}

.login-submit {
  width: 100%;
}

.login-tip {
  padding-top: 12px;
  border-top: 1px dashed var(--lr-border);
  text-align: center;

  p {
    margin: 4px 0;
    font-size: 12px;
    color: var(--lr-text-secondary);
  }

  b {
    color: var(--lr-text-regular);
  }
}
</style>
