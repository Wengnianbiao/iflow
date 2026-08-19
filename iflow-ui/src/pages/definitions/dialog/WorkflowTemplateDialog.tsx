import { useEffect, useState } from 'react'
import { Button, Modal } from 'antd'
import { CheckCircleIcon } from '@phosphor-icons/react'
import type { WorkflowTemplate } from '../workflowTemplates'
import { workflowTemplates } from '../workflowTemplates'
import styles from './WorkflowTemplateDialog.module.css'

type WorkflowTemplateDialogProps = {
  open: boolean
  onClose: () => void
  onSelect: (template: WorkflowTemplate) => void
}

export function WorkflowTemplateDialog({
  open,
  onClose,
  onSelect,
}: WorkflowTemplateDialogProps) {
  const [selectedId, setSelectedId] = useState<string>()

  useEffect(() => {
    if (!open) setSelectedId(undefined)
  }, [open])

  const selectedTemplate = workflowTemplates.find((template) => template.id === selectedId)

  return (
    <Modal
      title="选择工作流模板"
      open={open}
      width={760}
      destroyOnHidden
      onCancel={onClose}
      footer={(
        <div className={styles.footer}>
          <span>{selectedTemplate ? `已选择：${selectedTemplate.title}` : '选择一个模板后继续'}</span>
          <div>
            <Button onClick={onClose}>取消</Button>
            <Button
              type="primary"
              disabled={!selectedTemplate}
              onClick={() => selectedTemplate && onSelect(selectedTemplate)}
            >
              使用此模板
            </Button>
          </div>
        </div>
      )}
    >
      <p className={styles.intro}>从常用编排场景开始，进入编辑器后可自由调整任务与参数。</p>
      <div className={styles.grid} role="radiogroup" aria-label="工作流模板">
        {workflowTemplates.map((template) => {
          const selected = template.id === selectedId
          const TemplateIcon = template.icon
          return (
            <button
              type="button"
              key={template.id}
              className={`${styles.card} ${selected ? styles.selected : ''}`}
              role="radio"
              aria-checked={selected}
              onClick={() => setSelectedId(template.id)}
              onDoubleClick={() => onSelect(template)}
            >
              <span className={styles.icon} aria-hidden="true">
                <TemplateIcon size={23} weight="duotone" />
              </span>
              <span className={styles.content}>
                <strong>{template.title}</strong>
                <small>{template.description}</small>
                <span className={styles.tags}>
                  {template.taskTypes.map((taskType) => (
                    <code key={taskType}>{taskType}</code>
                  ))}
                </span>
              </span>
              {selected ? (
                <CheckCircleIcon className={styles.check} size={20} weight="fill" />
              ) : null}
            </button>
          )
        })}
      </div>
    </Modal>
  )
}
