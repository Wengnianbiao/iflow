package com.helianhealth.agent.common.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PageList<E> {

    private long total;

    private int pageNum;

    private int pageSize;

    private List<E> rows;


}
