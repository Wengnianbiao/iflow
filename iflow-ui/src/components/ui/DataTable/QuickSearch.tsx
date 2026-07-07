import type { ReactNode } from 'react'
import { Col, Input, Row, type ColProps } from 'antd'
import { dataTableStyles } from './styles'

export type QuickSearchProps = {
  autoFocusValue?: boolean
  createButton?: ReactNode
  description?: ReactNode
  onChange: (value: string) => void
  quickSearchLabel?: ReactNode
  quickSearchPlaceholder: string
  searchTerm: string
  searchModalContainerProps?: ColProps
}

/**
 * List quick-search panel aligned with Conductor ui-next QuickSearch.
 * Label sits on the input outline (top-left), matching ConductorInput / MUI TextField.
 */
export function QuickSearch({
  autoFocusValue = true,
  createButton,
  description,
  quickSearchLabel = '快速搜索',
  onChange,
  quickSearchPlaceholder,
  searchTerm,
  searchModalContainerProps,
}: QuickSearchProps) {
  return (
    <div style={dataTableStyles.quickSearchRoot}>
      <Row
        gutter={[16, 16]}
        justify="space-between"
        style={dataTableStyles.quickSearchRow}
      >
        <Col xs={24} md={8} {...searchModalContainerProps}>
          <div
            className="iflow-quick-search-field"
            style={dataTableStyles.quickSearchField}
          >
            <label
              className="iflow-quick-search-label"
              htmlFor="quick-search-field"
              style={dataTableStyles.quickSearchLabel}
            >
              {quickSearchLabel}
            </label>
            <Input
              id="quick-search-field"
              allowClear
              autoFocus={autoFocusValue}
              aria-label={
                typeof quickSearchLabel === 'string' ? quickSearchLabel : undefined
              }
              placeholder={quickSearchPlaceholder}
              size="large"
              value={searchTerm}
              onChange={(event) => onChange(event.target.value)}
            />
          </div>
        </Col>

        {createButton ? (
          <Col
            xs={24}
            md={6}
            style={dataTableStyles.quickSearchCreateButton}
          >
            {createButton}
          </Col>
        ) : null}

        {description ? (
          <Col
            xs={24}
            md={createButton ? 24 : 16}
            style={dataTableStyles.quickSearchDescription}
          >
            {description}
          </Col>
        ) : null}
      </Row>
    </div>
  )
}
