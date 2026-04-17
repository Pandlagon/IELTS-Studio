package com.ieltsstudio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * IELTS Studio 应用启动类
 *
 * <p>{@code @EnableAsync} 开启异步任务支持，
 * 用于试卷解析（{@link com.ieltsstudio.service.AsyncParseService}）
 * 和单词批量导入（{@link com.ieltsstudio.service.AsyncWordService}）的异步执行。
 */
@SpringBootApplication
@EnableAsync
public class IeltsStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(IeltsStudioApplication.class, args);
    }
}
