package com.zjw.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * 终端命令执行工具
 *
 * @author ZhangJw
 */
@Slf4j
@Component
public class TerminalOperationTool {

    @Tool("执行终端命令")
    public String executeCommand(@P("要执行的终端命令") String command) {
        log.info("执行命令: {}", command);
        try {
            Process process = new ProcessBuilder()
                    .command("cmd.exe", "/c", command)
                    .redirectErrorStream(true)
                    .start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "GBK"))) {
                String line;
                int lines = 0;
                while ((line = reader.readLine()) != null && lines < 100) {
                    output.append(line).append("\n");
                    lines++;
                }
            }

            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            if (!completed) {
                process.destroy();
                return "命令执行超时 (30秒)\n输出摘要:\n" + output;
            }

            int exitCode = process.exitValue();
            String result = output.toString();
            if (result.length() > 3000) {
                result = result.substring(0, 3000) + "\n... (输出过长已截断)";
            }

            return String.format("命令执行完成，退出码: %d\n输出:\n%s", exitCode, result);
        } catch (Exception e) {
            log.error("命令执行失败", e);
            return "命令执行失败: " + e.getMessage();
        }
    }
}
