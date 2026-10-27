package com.helianhealth.iflow.common.base;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class PageList<E> {

    private long total;

    private int pageNum;

    private int pageSize;

    private List<E> rows;

    public long getTotal() {
        return this.total;
    }

    public int getPageNum() {
        return this.pageNum;
    }

    public int getPageSize() {
        return this.pageSize;
    }

    public List<E> getRows() {
        return this.rows;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public void setRows(List<E> rows) {
        this.rows = rows;
    }

    protected boolean canEqual(Object other) {
        return other instanceof PageList;
    }

    public PageList(long total, int pageNum, int pageSize, List<E> rows) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.rows = rows;
    }

    public PageList() {
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PageList<?> other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return getTotal() == other.getTotal()
                && getPageNum() == other.getPageNum()
                && getPageSize() == other.getPageSize()
                && Objects.equals(getRows(), other.getRows());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getTotal(),
                getPageNum(),
                getPageSize(),
                getRows());
    }

    @Override
    public String toString() {
        return "PageList(total=" + getTotal()
                + ", pageNum=" + getPageNum()
                + ", pageSize=" + getPageSize()
                + ", rows=" + getRows() + ")";
    }

    public static class PageListBuilder<E> {

        private long total;
        private int pageNum;
        private int pageSize;
        private List<E> rows;

        PageListBuilder() {
        }

        public PageListBuilder<E> total(long total) {
            this.total = total;
            return this;
        }

        public PageListBuilder<E> pageNum(int pageNum) {
            this.pageNum = pageNum;
            return this;
        }

        public PageListBuilder<E> pageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public PageListBuilder<E> rows(List<E> rows) {
            this.rows = rows;
            return this;
        }

        public PageList<E> build() {
            return new PageList<>(
                    total,
                    pageNum,
                    pageSize,
                    rows);
        }

        @Override
        public String toString() {
            return "PageList.PageListBuilder(total=" + total
                    + ", pageNum=" + pageNum
                    + ", pageSize=" + pageSize
                    + ", rows=" + rows + ")";
        }
    }

    public static <E> PageListBuilder<E> builder() {
        return new PageListBuilder<>();
    }
}
