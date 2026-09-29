package com.helianhealth.iflow.common.domain.run;

import java.io.IOException;
import java.io.InputStream;

/**
 * 一次工作流执行期间的临时文件访问能力。
 *
 * <p>每次调用必须返回独立的流；实现可以在工作流返回后关闭，此后引用不可再读取。
 */
@FunctionalInterface
public interface FileAccess {

    InputStream open(String fileRef) throws IOException;
}
