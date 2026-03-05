package com.helianhealth.agent.extension;

import org.springframework.stereotype.Component;

/**
 * 使用场景:
 * 对于在请求体内的动态参数,可以自定义一个bean
 * 使用映射类型Bean表达式 @testBean.getPatientName(#data) 来获取动态参数
 */
@Component("testBean")
public class TestBean {

    public String getPatientName(String patientId) {

        // do something

        return "张三";
    }
}
