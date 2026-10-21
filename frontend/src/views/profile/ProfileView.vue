<template>
  <div class="page-container">
    <el-row :gutter="16">
      <!-- ---------- 我的档案 ---------- -->
      <el-col :xs="24" :md="14">
        <el-card v-loading="loading" shadow="never">
          <template #header>
            <div class="card-header">
              <span>我的档案</span>
              <el-tag size="small" effect="plain">{{ profile?.roleName ?? '' }}</el-tag>
            </div>
          </template>

          <el-descriptions v-if="profile" :column="2" border size="small">
            <el-descriptions-item label="账号">{{ profile.username }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ profile.realName }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ profile.phone || '—' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ profile.email || '—' }}</el-descriptions-item>
            <el-descriptions-item label="邮件通知">
              {{ profile.notifyEmail === 1 ? '已开启' : '未开启' }}
            </el-descriptions-item>
            <el-descriptions-item label="最后登录">{{ profile.lastLoginAt ?? '—' }}</el-descriptions-item>

            <template v-if="profile.studentInfo">
              <el-descriptions-item label="学号">{{ profile.studentInfo.studentNo }}</el-descriptions-item>
              <el-descriptions-item label="班级">{{ profile.studentInfo.className }}</el-descriptions-item>
              <el-descriptions-item label="学院">{{ profile.studentInfo.college }}</el-descriptions-item>
              <el-descriptions-item label="专业">{{ profile.studentInfo.major }}</el-descriptions-item>
            </template>

            <template v-if="profile.teacherInfo">
              <el-descriptions-item label="工号">{{ profile.teacherInfo.teacherNo }}</el-descriptions-item>
              <el-descriptions-item label="学院">{{ profile.teacherInfo.college }}</el-descriptions-item>
              <el-descriptions-item label="职称">{{ profile.teacherInfo.title || '—' }}</el-descriptions-item>
            </template>

            <template v-if="profile.staffInfo">
              <el-descriptions-item label="工号">{{ profile.staffInfo.staffNo }}</el-descriptions-item>
              <el-descriptions-item label="部门">{{ profile.staffInfo.department || '—' }}</el-descriptions-item>
              <el-descriptions-item v-if="profile.staffInfo.maintainerType" label="维修类别">
                {{ profile.staffInfo.maintainerTypeName }}
              </el-descriptions-item>
            </template>
          </el-descriptions>

          <el-alert
            class="mt-16"
            type="info"
            :closable="false"
            show-icon
            title="姓名、角色、账号、学号工号由系统管理员维护，如需变更请联系管理员"
          />
        </el-card>
      </el-col>

      <!-- ---------- 可改资料 ---------- -->
      <el-col :xs="24" :md="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>联系方式与通知</span>
            </div>
          </template>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" placeholder="11 位手机号" maxlength="11" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="用于接收通知邮件" />
            </el-form-item>
            <el-form-item label="邮件通知">
              <el-switch v-model="form.notifyEmail" :active-value="1" :inactive-value="0" />
              <span class="tip">开启后，站内信会同时尝试发送邮件</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
              <el-button @click="loadProfile">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getSelfProfile, updateSelf, type UserDetail } from '@/api/user'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const profile = ref<UserDetail | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({ phone: '', email: '', notifyEmail: 0 })

const rules: FormRules = {
  phone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

async function loadProfile(): Promise<void> {
  loading.value = true
  try {
    profile.value = await getSelfProfile()
    form.phone = profile.value.phone ?? ''
    form.email = profile.value.email ?? ''
    form.notifyEmail = profile.value.notifyEmail ?? 0
  } finally {
    loading.value = false
  }
}

async function handleSave(): Promise<void> {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    await updateSelf({ phone: form.phone, email: form.email, notifyEmail: form.notifyEmail })
    ElMessage.success('已保存')
    await loadProfile()
    // 顶栏展示的信息也可能变化，顺手刷新一次
    await userStore.fetchInfo()
  } finally {
    saving.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mt-16 {
  margin-top: 16px;
}

.tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--lr-text-secondary);
}
</style>
