package com.helianhealth.agent.common.utils;


import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.dubbo.common.URL;

@Data
@NoArgsConstructor
public class UrlWrapper {

    public static final String AGENT_URL = "agent://127.0.0.1:8099";

    protected URL url;

    public UrlWrapper initUrl(){
        url = URL.valueOf(AGENT_URL);
        return this;
    }

    public URL get(){
        return url;
    }

    public UrlWrapper remote(String remote){
        url = url.addParameter("remote", remote);
        return this;
    }

    public UrlWrapper node(String node){
        url = url.addParameter("node", node);
        return this;
    }

    public UrlWrapper proxy(String proxy){
        url = url.addParameter("proxy", proxy);
        return this;
    }

    public UrlWrapper mock(Integer mockId){
        url = url.addParameter("mock", mockId);
        return this;
    }
}
