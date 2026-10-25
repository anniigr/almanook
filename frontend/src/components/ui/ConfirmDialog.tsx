import { Button } from './Button'
import { Dialog } from './Dialog'

export function ConfirmDialog(props: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: string
  confirmLabel: string
  onConfirm: () => void
  loading?: boolean
}) {
  return (
    <Dialog
      open={props.open}
      onOpenChange={props.onOpenChange}
      title={props.title}
      description={props.description}
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={() => props.onOpenChange(false)}>
            Cancel
          </Button>
          <Button variant="danger" onClick={props.onConfirm} loading={props.loading}>
            {props.confirmLabel}
          </Button>
        </>
      }
    >
      <span />
    </Dialog>
  )
}
