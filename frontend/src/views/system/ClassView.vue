<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="班级名 / 专业" clearable class="w-160" />
        </el-form-item>
        <el-form-item label="学院">
          <el-input v-model="query.college" placeholder="如 计算机学院" clearable class="w-160" />
        </el-form-item>
        <el-form-item label="年级">
          <el-input v-model.number="query.grade" placeholder="如 2021" clearable class="w-120" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable class="w-120">
            <el-option label="正常" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button v-if="canManage" type="success" :icon="Plus" @click="openCreate">新增班级</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="className" label="班级" width="140" />
        <el-table-column prop="college" label="学院" min-width="150" />
        <el-table-column prop="major" label="专业" min-width="150" />
        <el-table-column prop="grade" label="年级" width="90" align="center" />
        <el-table-column prop="studentCount" label="班级人数" width="100" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="canManage" label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的班级" />
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

    <el-dialog v-model="dialogVisible" :title="isCreate ? '新增班级' : '编辑班级'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="班级名称" prop="className">
          <el-input v-model="form.className" placeholder="如 软工2103" />
        </el-form-item>
        <el-form-item label="所属学院" prop="college">
          <el-input v-model="form.college" placeholder="如 计算机学院" />
        </el-form-item>
        <el-form-item label="专业" prop="major">
          <el-input v-model="form.major" placeholder="如 软件工程" />
        </el-form-item>
        <el-form-item label="年级" prop="grade">
          <el-input v-model.number="form.grade" placeholder="如 2021" />
        </el-form-item>
        <el-form-item label="班级人数">
          <el-input v-model.number="form.studentCount" placeholder="教务口径的班级规模" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="停用" />
        </el-form-item>
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
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { createClass, getClassPage, updateClass, type ClassItem, type ClassQuery, type ClassSavePayload } from '@/api/class'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canManage = computed(() => userStore.hasPermission('class:manage'))

const loading = ref(false)
const rows = ref<ClassItem[]>([])
const total = ref(0)

const query = reactive<ClassQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: null,
  college: null,
  grade: null,
  status: null
})

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const result = await getClassPage(query)
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
  query.college = null
  query.grade = null
  query.status = null
  handleSearch()
}

// ---------- 表单 ----------
interface ClassForm {
  id: number | null
  className: string
  college: string
  major: string
  grade: number | null
  studentCount: number | null
  status: number
}

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<ClassForm>(emptyForm())
const isCreate = computed(() => form.id === null)

function emptyForm(): ClassForm {
  return { id: null, className: '', college: '', major: '', grade: null, studentCount: 0, status: 1 }
}

const rules: FormRules<ClassForm> = {
  className: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
  college: [{ required: true, message: '请输入所属学院', trigger: 'blur' }],
  major: [{ required: true, message: '请输入专业', trigger: 'blur' }],
  grade: [{ required: true, message: '请输入年级', trigger: 'blur' }]
}

function openCreate(): void {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: ClassItem): void {
  Object.assign(form, {
    id: row.id,
    className: row.className,
    college: row.college,
    major: row.major,
    grade: row.grade,
    studentCount: row.studentCount,
    status: row.status
  })
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
    const payload: ClassSavePayload = {
      id: form.id,
      className: form.className.trim(),
      college: form.college.trim(),
      major: form.major.trim(),
      grade: form.grade as number,
      studentCount: form.studentCount ?? 0,
      status: form.status
    }
    if (isCreate.value) {
      await createClass(payload)
      ElMessage.success('新增成功')
    } else {
      await updateClass(payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await loadPage()
  } finally {
    submitting.value = false
  }
}

onMounted(loadPage)
</script>

<style scoped lang="scss">
.filter-card {
  margin-bottom: 16px;
}

.w-120 {
  width: 120px;
}

.w-160 {
  width: 160px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
