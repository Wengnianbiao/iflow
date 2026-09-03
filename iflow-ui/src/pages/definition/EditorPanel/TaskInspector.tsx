import { getTaskPlugin } from '@/tasks'
import type { TaskFormProps } from './TaskFormTab/forms'
import { UnknownTaskForm } from './TaskFormTab/forms'

export function getTaskForm(type: string) {
  return getTaskPlugin(type)?.form ?? UnknownTaskForm
}

export function TaskInspector(props: TaskFormProps) {
  const TaskForm = getTaskForm(props.task.type)
  return <TaskForm {...props} />
}
