<template>
  <div class="page-container">
    <!-- ---------- 筛选 ---------- -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline label-width="72px" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="账号 / 姓名" clearable class="w-160" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="query.roleCode" placeholder="全部" clearable class="w-140">
            <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="学院">
          <el-input v-model="query.college" placeholder="如 计算机学院" clearable class="w-160" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable class="w-120">
            <el-option label="正常" :value="1" />
            <el-option label="已注销" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button v-if="canCreate" type="success" :icon="Plus" @click="openCreate">新增用户</el-button>
        </el-form-item>
        <el-form-item v-if="canViewPhone" label="手机号">
          <el-switch
            v-model="query.revealPhone"
            active-text="显示完整"
            inactive-text="脱敏"
            @change="handleSearch"
          />
          <el-tooltip content="查看完整手机号会写入一条 QUERY_PHONE 操作日志" placement="top">
            <el-icon class="ml-4"><InfoFilled /></el-icon>
          </el-tooltip>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ---------- 列表 ---------- -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="username" label="账号" width="130" />
        <el-table-column prop="realName" label="姓名" width="110" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.roleName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="学号 / 工号" width="140">
          <template #default="{ row }">
            {{ row.studentNo || row.teacherNo || row.staffNo || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="班级 / 部门" min-width="150">
          <template #default="{ row }">
            <span v-if="row.className">{{ row.className }}</span>
            <span v-else-if="row.department">{{ row.department }}</span>
            <span v-else-if="row.college">{{ row.college }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="手机号" width="140">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '正常' : '已注销' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="canUpdate" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="canDisable"
              link
              :type="row.status === 1 ? 'danger' : 'success'"
              :disabled="row.id === currentUserId"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '注销' : '恢复' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的用户" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="loadPage"
        @size-change="handleSearch"
      />
    </el-card>

    <!-- ---------- 新增 / 编辑 ---------- -->
    <el-dialog
      v-model="dialogVisible"
      :title="isCreate ? '新增用户' : '编辑用户'"
      width="640px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" :disabled="!isCreate" placeholder="学号 / 工号 / 管理员账号" />
        </el-form-item>
        <el-form-item v-if="isCreate" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="6~64 位" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-select v-if="isCreate" v-model="form.roleCode" class="w-full" placeholder="请选择角色">
            <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
          <el-tag v-else type="info">{{ roleLabel(form.roleCode) }}（角色不可修改）</el-tag>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="用于接收通知邮件" />
        </el-form-item>
        <el-form-item label="邮件通知">
          <el-switch v-model="form.notifyEmail" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <!-- 学生区 -->
        <template v-if="form.roleCode === 'STUDENT'">
          <el-divider content-position="left">学生档案</el-divider>
          <el-form-item label="学号" prop="studentNo">
            <el-input v-model="form.studentNo" :disabled="!isCreate" placeholder="如 2021002" />
          </el-form-item>
          <el-form-item label="班级" prop="classId">
            <el-select v-model="form.classId" filterable class="w-full" placeholder="选择班级">
              <el-option
                v-for="c in classes"
                :key="c.id"
                :label="`${c.className}（${c.college} · ${c.major}）`"
                :value="c.id"
              />
            </el-select>
          </el-form-item>
        </template>

        <!-- 教师区 -->
        <template v-else-if="form.roleCode === 'TEACHER'">
          <el-divider content-position="left">教师档案</el-divider>
          <el-form-item label="工号" prop="teacherNo">
            <el-input v-model="form.teacherNo" :disabled="!isCreate" placeholder="如 T1005" />
          </el-form-item>
          <el-form-item label="所属学院" prop="college">
            <el-input v-model="form.college" placeholder="如 计算机学院" />
          </el-form-item>
          <el-form-item label="职称">
            <el-input v-model="form.title" placeholder="如 副教授" />
          </el-form-item>
        </template>

        <!-- 管理类区 -->
        <template v-else-if="form.roleCode">
          <el-divider content-position="left">人员档案</el-divider>
          <el-form-item label="工号" prop="staffNo">
            <el-input v-model="form.staffNo" :disabled="!isCreate" placeholder="如 M0003" />
          </el-form-item>
          <el-form-item label="部门">
            <el-input v-model="form.department" placeholder="如 后勤维修科" />
          </el-form-item>
          <el-form-item v-if="form.roleCode === 'MAINTAINER'" label="维修类别">
            <el-radio-group v-model="form.maintainerType">
              <el-radio :value="1">校内</el-radio>
              <el-radio :value="2">校外</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { InfoFilled, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createUser,
  getUserInfo,
  getUserPage,
  updateUser,
  updateUserStatus,
  type RoleCode,
  type UserCreatePayload,
  type UserQuery,
  type UserRow,
  type UserUpdatePayload
} from '@/api/user'
import { getClassList, type ClassItem } from '@/api/class'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('user:create'))
const canUpdate = computed(() => userStore.hasPermission('user:update'))
const canDisable = computed(() => userStore.hasPermission('user:disable'))
const canViewPhone = computed(() => userStore.hasPermission('user:view-phone'))
const currentUserId = computed(() => userStore.userInfo?.id ?? -1)

const roleOptions = [
  { value: 'STUDENT', label: '学生' },
  { value: 'TEACHER', label: '教师' },
  { value: 'SYS_ADMIN', label: '系统管理员' },
  { value: 'LAB_ADMIN', label: '实验室管理员' },
  { value: 'MAINTAINER', label: '维修人员' }
]

function roleLabel(code: string): string {
  return roleOptions.find((r) => r.value === code)?.label ?? code
}

// ---------- 列表 ----------
const loading = ref(false)
const rows = ref<UserRow[]>([])
const total = ref(0)
const classes = ref<ClassItem[]>([])

const query = reactive<UserQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: null,
  roleCode: null,
  college: null,
  status: null,
  revealPhone: false
})

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const result = await getUserPage(query)
    rows.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  query.pageNum = 1
  void loadPage()
}

function handleReset(): void {
  query.keyword = null
  query.roleCode = null
  query.college = null
  query.status = null
  query.revealPhone = false
  handleSearch()
}

// ---------- 表单 ----------
interface UserForm {
  id: number | null
  username: string
  password: string
  realName: string
  roleCode: RoleCode | ''
  phone: string
  email: string
  notifyEmail: number
  studentNo: string
  classId: number | null
  teacherNo: string
  college: string
  title: string
  staffNo: string
  department: string
  maintainerType: number | null
}

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<UserForm>(emptyForm())
const isCreate = computed(() => form.id === null)

function emptyForm(): UserForm {
  return {
    id: null,
    username: '',
    password: '',
    realName: '',
    roleCode: '',
    phone: '',
    email: '',
    notifyEmail: 0,
    studentNo: '',
    classId: null,
    teacherNo: '',
    college: '',
    title: '',
    staffNo: '',
    department: '',
    maintainerType: 1
  }
}

function resetForm(): void {
  Object.assign(form, emptyForm())
}

/** 校验规则：账号/密码只在新增时必填；扩展字段按角色必填 */
const rules = computed<FormRules<UserForm>>(() => ({
  username: isCreate.value ? [{ required: true, message: '请输入账号', trigger: 'blur' }] : [],
  password: isCreate.value
    ? [
        { required: true, message: '请输入初始密码', trigger: 'blur' },
        { min: 6, max: 64, message: '密码长度需在 6~64 位之间', trigger: 'blur' }
      ]
    : [],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  roleCode: isCreate.value ? [{ required: true, message: '请选择角色', trigger: 'change' }] : [],
  phone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  studentNo: form.roleCode === 'STUDENT' ? [{ required: true, message: '请输入学号', trigger: 'blur' }] : [],
  classId: form.roleCode === 'STUDENT' ? [{ required: true, message: '请选择班级', trigger: 'change' }] : [],
  teacherNo: form.roleCode === 'TEACHER' ? [{ required: true, message: '请输入工号', trigger: 'blur' }] : [],
  college: form.roleCode === 'TEACHER' ? [{ required: true, message: '请填写所属学院', trigger: 'blur' }] : [],
  staffNo:
    form.roleCode && form.roleCode !== 'STUDENT' && form.roleCode !== 'TEACHER'
      ? [{ required: true, message: '请输入工号', trigger: 'blur' }]
      : []
}))

function openCreate(): void {
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: UserRow): Promise<void> {
  resetForm()
  const detail = await getUserInfo(row.id)
  form.id = detail.id
  form.username = detail.username
  form.realName = detail.realName
  form.roleCode = detail.roleCode
  form.phone = detail.phone ?? ''
  form.email = detail.email ?? ''
  form.notifyEmail = detail.notifyEmail ?? 0
  if (detail.studentInfo) {
    form.studentNo = detail.studentInfo.studentNo
    form.classId = detail.studentInfo.classId
  }
  if (detail.teacherInfo) {
    form.teacherNo = detail.teacherInfo.teacherNo
    form.college = detail.teacherInfo.college
    form.title = detail.teacherInfo.title ?? ''
  }
  if (detail.staffInfo) {
    form.staffNo = detail.staffInfo.staffNo
    form.department = detail.staffInfo.department ?? ''
    form.maintainerType = detail.staffInfo.maintainerType ?? 1
  }
  dialogVisible.value = true
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    if (isCreate.value) {
      await createUser(buildCreatePayload())
      ElMessage.success('新增成功')
    } else {
      await updateUser(buildUpdatePayload())
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await loadPage()
  } finally {
    submitting.value = false
  }
}

function buildCreatePayload(): UserCreatePayload {
  const payload: UserCreatePayload = {
    username: form.username.trim(),
    password: form.password,
    realName: form.realName.trim(),
    roleCode: form.roleCode as RoleCode,
    phone: form.phone,
    email: form.email,
    notifyEmail: form.notifyEmail
  }
  if (form.roleCode === 'STUDENT') {
    payload.studentInfo = { studentNo: form.studentNo.trim(), classId: form.classId as number }
  } else if (form.roleCode === 'TEACHER') {
    payload.teacherInfo = { teacherNo: form.teacherNo.trim(), college: form.college, title: form.title }
  } else {
    payload.staffInfo = {
      staffNo: form.staffNo.trim(),
      department: form.department,
      maintainerType: form.roleCode === 'MAINTAINER' ? form.maintainerType : null
    }
  }
  return payload
}

function buildUpdatePayload(): UserUpdatePayload {
  const payload: UserUpdatePayload = {
    id: form.id as number,
    realName: form.realName.trim(),
    phone: form.phone,
    email: form.email,
    notifyEmail: form.notifyEmail
  }
  if (form.roleCode === 'STUDENT') {
    payload.studentInfo = { classId: form.classId as number }
  } else if (form.roleCode === 'TEACHER') {
    payload.teacherInfo = { college: form.college, title: form.title }
  } else {
    payload.staffInfo = {
      department: form.department,
      maintainerType: form.roleCode === 'MAINTAINER' ? form.maintainerType : null
    }
  }
  return payload
}

// ---------- 注销 / 恢复 ----------
async function toggleStatus(row: UserRow): Promise<void> {
  const disabling = row.status === 1
  try {
    const { value } = await ElMessageBox.prompt(
      disabling ? `确定注销「${row.realName}」吗？注销后该账号不能登录，但历史数据保留。` : `确定恢复「${row.realName}」吗？`,
      disabling ? '注销用户' : '恢复用户',
      {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPlaceholder: disabling ? '注销原因（会写入操作日志）' : '备注（可留空）',
        inputValue: '',
        inputValidator: (val: string) => (!disabling || !!val?.trim() ? true : '请填写注销原因')
      }
    )
    await updateUserStatus({ id: row.id, status: disabling ? 0 : 1, reason: value })
    ElMessage.success(disabling ? '已注销' : '已恢复')
    await loadPage()
  } catch {
    // 取消
  }
}

onMounted(async () => {
  classes.value = await getClassList()
  await loadPage()
})
</script>

<style scoped lang="scss">
.filter-card {
  margin-bottom: 16px;
}

.w-120 {
  width: 120px;
}

.w-140 {
  width: 140px;
}

.w-160 {
  width: 160px;
}

.w-full {
  width: 100%;
}

.ml-4 {
  margin-left: 4px;
  color: var(--lr-text-secondary);
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
