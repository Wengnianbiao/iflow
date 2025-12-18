package com.helianhealth.agent.log.controller;

import com.helianhealth.agent.common.PageList;
import com.helianhealth.agent.common.ResultData;
import com.helianhealth.agent.log.model.InterfaceInvokeLogDO;
import com.helianhealth.agent.log.service.InterfaceInvokeLogService;
import com.helianhealth.agent.utils.ResponseRenderUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/console/api-log")
@Slf4j
@AllArgsConstructor
public class ApiLogController {

    private final InterfaceInvokeLogService interfaceInvokeLogService;

    /**
     * 获取所有节点参数配置
     */
    @GetMapping("/all")
    public ResultData<PageList<InterfaceInvokeLogDO>> findAllInvokeLogs(
            @RequestParam(value = "nodeName", required = false) String nodeName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        try {
            PageList<InterfaceInvokeLogDO> pageList = interfaceInvokeLogService.findAllInvokeLogs(nodeName, keyword, pageNum, pageSize);
            return ResponseRenderUtils.render(pageList);
        } catch (Exception e) {
            log.error("获取节点参数配置列表失败", e);
            return ResponseRenderUtils.error("获取节点参数配置列表失败: " + e.getMessage());
        }
    }
}
