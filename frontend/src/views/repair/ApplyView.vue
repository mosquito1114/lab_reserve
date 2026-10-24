<template>
  <div class="page-container">
    <el-card shadow="never" header="我要报修">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" style="max-width: 760px">
        <el-form-item label="所属实验室">
          <el-select v-model="labId" clearable filterable placeholder="先选实验室可缩小设备范围" style="width: 100%" @change="loadEquipments">
            <el-option v-for="item in labs" :key="item.id" :label="`${item.labNo} ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="报修设备" prop="equipmentId">
          <el-select v-model="form.equipmentId" filterable placeholder="请选择设备" style="width: 100%">
            <el-option v-for="item in equipments" :key="item.id" :label="`${item.resourceNo} ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="故障现象" prop="faultDesc">
          <el-input v-model="form.faultDesc" type="textarea" :rows="3" maxlength="1000" show-word-limit
                    placeholder="如：通电后屏幕无显示，风扇不转" />
        </el-form-item>
        <el-form-item label="发生场景" prop="faultScene">
          <el-input v-model="form.faultScene" maxlength="255" placeholder="如：2026-10-26 上午第三节课，做操作系统实验时" />
        </el-form-item>
        <el-form-item label="故障截图">
          <el-upload
            v-model:file-list="fileList"
            :http-request="handleUpload"
            :before-upload="beforeUpload"
            :on-remove="handleRemove"
            list-type="picture-card"
            accept="image/jpeg,image/jpg,image/png,image/webp"
            :limit="5"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
          <div class="text-secondary">最多 5 张，单张 ≤ 5MB，支持 jpg / png / webp（服务端会校验文件头）</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交报修</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules, type UploadFile, type UploadRequestOptions } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { createRepair, uploadImage } from '@/api/repair'
import { listEquipmentOptions, listLaboratoryOptions, type EquipmentItem, type LaboratoryOption } from '@/api/resource'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const labs = ref<LaboratoryOption[]>([])
const equipments = ref<EquipmentItem[]>([])
const labId = ref<number | null>(null)
const fileList = ref<UploadFile[]>([])

const form = reactive<{ equipmentId: number | null; faultDesc: string; faultScene: string }>({
  equipmentId: null,
  faultDesc: '',
  faultScene: ''
})

const rules: FormRules<typeof form> = {
  equipmentId: [{ required: true, message: '请选择报修设备', trigger: 'change' }],
  faultDesc: [{ required: true, message: '请填写故障现象', trigger: 'blur' }],
  faultScene: [{ required: true, message: '请填写发生场景', trigger: 'blur' }]
}

function beforeUpload(file: File): boolean {
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('单张图片不能超过 5MB')
    return false
  }
  const ok = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'].includes(file.type)
  if (!ok) {
    ElMessage.warning('仅支持 jpg / png / webp 图片')
    return false
  }
  return true
}

async function handleUpload(options: UploadRequestOptions): Promise<void> {
  try {
    const result = await uploadImage(options.file)
    const target = fileList.value.find((item) => item.uid === options.file.uid)
    if (target) {
      target.url = result.url
      target.response = result
    }
    ElMessage.success('上传成功')
  } catch (error) {
    ElMessage.error((error as Error).message)
    fileList.value = fileList.value.filter((item) => item.uid !== options.file.uid)
  }
}

function handleRemove(file: UploadFile): void {
  fileList.value = fileList.value.filter((item) => item.uid !== file.uid)
}

async function loadEquipments(): Promise<void> {
  equipments.value = await listEquipmentOptions({ labId: labId.value, onlyAvailable: false })
  form.equipmentId = (equipments.value[0]?.id ?? null) as number | null
}

function resetForm(): void {
  form.equipmentId = null
  form.faultDesc = ''
  form.faultScene = ''
  fileList.value = []
  labId.value = null
  void loadEquipments()
}

async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  const images = fileList.value
    .map((item) => (item.response as { url?: string } | undefined)?.url ?? item.url)
    .filter((url): url is string => Boolean(url))
  submitting.value = true
  try {
    const order = await createRepair({
      equipmentId: form.equipmentId as number,
      faultDesc: form.faultDesc,
      faultScene: form.faultScene,
      faultImages: images
    })
    ElMessage.success(`工单已提交：${order.orderNo}`)
    resetForm()
    void router.push('/repair/my')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  labs.value = await listLaboratoryOptions({ status: null })
  await loadEquipments()
})
</script>
