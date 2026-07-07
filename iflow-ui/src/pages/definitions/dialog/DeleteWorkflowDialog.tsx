import { useEffect, useState } from 'react'
import { Alert, Input, Modal } from 'antd'
import { deleteWorkflowDefinition } from '@/api/workflowDefinition'
import { useMessageContext } from '@/components/providers/messageContext'
import type { WorkflowDef } from '@/types/workflow'

type DeleteWorkflowDialogProps = {
  definition: WorkflowDef | null
  onClose: () => void
  onSuccess: () => void
}

export function DeleteWorkflowDialog({
  definition,
  onClose,
  onSuccess,
}: DeleteWorkflowDialogProps) {
  const { setMessage } = useMessageContext()
  const [submitting, setSubmitting] = useState(false)
  const [confirmName, setConfirmName] = useState('')

  useEffect(() => {
    if (definition) setConfirmName('')
  }, [definition])

  const nameMatched = Boolean(definition && confirmName === definition.name)

  const handleDelete = async () => {
    if (!definition || !nameMatched) return

    try {
      setSubmitting(true)
      await deleteWorkflowDefinition(definition.name, definition.version)
      onSuccess()
    } catch (reason) {
      setMessage({
        severity: 'error',
        text: reason instanceof Error ? reason.message : '删除工作流失败',
      })
    } finally {
      setSubmitting(false)
    }
  }

  const handleClose = () => {
    setConfirmName('')
    onClose()
  }

  return (
    <Modal
      title="删除确认"
      open={Boolean(definition)}
      okText="确认删除"
      cancelText="取消"
      okButtonProps={{ danger: true, disabled: !nameMatched }}
      confirmLoading={submitting}
      destroyOnHidden
      onCancel={handleClose}
      onOk={() => void handleDelete()}
    >
      <p>
        确定删除工作流 <strong style={{ color: 'var(--iflow-color-danger)' }}>{definition?.displayName}</strong>{' '}
        （{definition?.name}）的版本 {definition?.version} 吗？此操作无法撤销。
      </p>
      <p>
        请输入工作流名称 <strong>{definition?.name}</strong> 以确认。
      </p>
      <Input
        autoFocus
        spellCheck={false}
        placeholder={definition?.name}
        value={confirmName}
        onChange={(event) => setConfirmName(event.target.value)}
        onPressEnter={() => {
          if (nameMatched) void handleDelete()
        }}
      />
      <Alert
        style={{ marginTop: 12 }}
        type="warning"
        showIcon
        message="删除后无法恢复，请确认当前版本不再使用。"
      />
    </Modal>
  )
}
