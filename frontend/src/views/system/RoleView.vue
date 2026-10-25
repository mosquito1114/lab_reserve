<template>
  <div class="page-container">
    <el-row :gutter="16">
      <!-- ---------- 左：角色列表 ---------- -->
      <el-col :xs="24" :md="8">
        <el-card v-loading="loadingRoles" shadow="never">
          <template #header>
            <div class="card-header">
              <span>角色</span>
              <el-tag size="small" type="info">共 {{ roles.length }} 个固定角色</el-tag>
            </div>
          </template>

          <el-table
            :data="roles"
            highlight-current-row
            :row-class-name="rowClass"
            @current-change="handleCurrentChange"
          >
            <el-table-column label="角色" min-width="140">
              <template #default="{ row }">
                <div class="role-name">{{ row.name }}</div>
                <div class="role-code">{{ row.code }}</div>
              </template>
            </el-table-column>
            <el-table-column label="权限数" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.permissionCount }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="用户数" width="80" align="center">
              <template #default="{ row }">{{ row.userCount }}</template>
            </el-table-column>
          </el-table>

          <div class="role-tip">
            权限是<b>数据</b>而不是代码：勾选保存后立即生效，无需重启后端。
          </div>
        </el-card>
      </el-col>

      <!-- ---------- 右：权限勾选树 ---------- -->
      <el-col :xs="24" :md="16">
        <el-card v-loading="loadingTree" shadow="never">
          <template #header>
            <div class="card-header">
              <span>
                权限配置
                <el-tag v-if="currentRole" class="ml-8" size="small" type="primary">
                  {{ currentRole.name }}（{{ checkedCount }} 项）
                </el-tag>
              </span>
              <el-button
                v-if="canEdit"
                type="primary"
                :loading="saving"
                :disabled="!currentRole"
                @click="handleSave"
              >
                保存
              </el-button>
            </div>
          </template>

          <el-empty v-if="!currentRole" description="请在左侧选择一个角色" />
          <el-tree
            v-else
            ref="treeRef"
            class="permission-tree"
            :data="treeData"
            show-checkbox
            default-expand-all
            node-key="id"
            :props="{ label: 'label', children: 'children' }"
            @check="handleCheck"
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, type ElTree } from 'element-plus'
import { getRoleList, getRolePermissionList, updateRolePermission, type RoleItem } from '@/api/role'
import { getPermissionList } from '@/api/permission'
import { useUserStore } from '@/stores/user'

/** 树节点：分组节点 id 用字符串前缀，叶子节点 id 是权限 ID */
interface TreeNode {
  id: number | string
  label: string
  children?: TreeNode[]
}

const userStore = useUserStore()
const canEdit = computed(() => userStore.hasPermission('system:role:update'))

const loadingRoles = ref(false)
const loadingTree = ref(false)
const saving = ref(false)

const roles = ref<RoleItem[]>([])
const currentRole = ref<RoleItem | null>(null)
const treeData = ref<TreeNode[]>([])
const checkedCount = ref(0)

const treeRef = ref<InstanceType<typeof ElTree>>()

/** 当前选中角色高亮 */
function rowClass({ row }: { row: RoleItem }): string {
  return row.id === currentRole.value?.id ? 'is-current-role' : ''
}

async function loadRoles(): Promise<void> {
  loadingRoles.value = true
  try {
    roles.value = await getRoleList()
    if (roles.value.length > 0) {
      // 默认选中第一个角色，避免右侧空白
      currentRole.value = roles.value[0]
      await loadRolePermissions(currentRole.value.id)
    }
  } finally {
    loadingRoles.value = false
  }
}

async function loadTree(): Promise<void> {
  loadingTree.value = true
  try {
    const groups = await getPermissionList()
    treeData.value = groups.map((group) => ({
      id: `module:${group.module}`,
      label: group.moduleName,
      children: group.items.map((item) => ({
        id: item.id,
        label: `${item.name}（${item.code}）`
      }))
    }))
  } finally {
    loadingTree.value = false
  }
}

async function loadRolePermissions(roleId: number): Promise<void> {
  loadingTree.value = true
  try {
    const detail = await getRolePermissionList(roleId)
    await nextTick()
    // 只勾选叶子节点，父分组会由 el-tree 自动推导半选/全选状态
    treeRef.value?.setCheckedKeys(detail.permissionIds, false)
    checkedCount.value = detail.permissionIds.length
  } finally {
    loadingTree.value = false
  }
}

function handleCheck(): void {
  checkedCount.value = leafCheckedIds().length
}

async function handleCurrentChange(row: RoleItem | null): Promise<void> {
  if (!row || row.id === currentRole.value?.id) {
    return
  }
  currentRole.value = row
  await loadRolePermissions(row.id)
}

/** 取勾选的叶子节点 ID（分组节点是字符串 id，天然被过滤掉） */
function leafCheckedIds(): number[] {
  const keys = treeRef.value?.getCheckedKeys(true) ?? []
  return keys.filter((key): key is number => typeof key === 'number')
}

async function handleSave(): Promise<void> {
  if (!currentRole.value) {
    return
  }
  const permissionIds = leafCheckedIds()
  saving.value = true
  try {
    await updateRolePermission(currentRole.value.id, permissionIds)
    ElMessage.success(`已保存「${currentRole.value.name}」的 ${permissionIds.length} 项权限`)
    // 刷新计数，并让本地权限立即生效（当前用户可能就是被改的角色）
    await loadRoles()
    await userStore.fetchInfo()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadTree()
  await loadRoles()
})
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ml-8 {
  margin-left: 8px;
}

.role-name {
  font-size: 14px;
  color: var(--lr-text-primary);
}

.role-code {
  font-size: 12px;
  color: var(--lr-text-secondary);
}

.role-tip {
  margin-top: 12px;
  padding-top: 12px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--lr-text-secondary);
  border-top: 1px dashed var(--lr-border);
}

.permission-tree {
  max-height: 62vh;
  overflow-y: auto;
}

:deep(.is-current-role) {
  background-color: var(--lr-primary-soft);
}
</style>
