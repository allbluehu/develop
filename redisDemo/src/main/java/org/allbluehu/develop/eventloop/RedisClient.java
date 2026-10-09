package org.allbluehu.develop.eventloop;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

/**
 * 简单的 Redis 测试客户端
 */
public class RedisClient {

    public static void main(String[] args) throws Exception {
        Socket socket = new Socket("localhost", 6379);
        OutputStream out = socket.getOutputStream();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
        );

        // 测试命令
        String[] commands = {
                "SET mykey hello",
                "SET mykey world",     // 覆盖上面的
                "GET mykey",
                "DEL mykey",
                "GET mykey",
                "PING"
        };

        for (String cmd : commands) {
            System.out.println(">>> " + cmd);
            out.write((cmd + "\r\n").getBytes());
            out.flush();

            // 读取响应
            StringBuilder response = new StringBuilder();
            char c;
            while ((c = (char) in.read()) != '\n') {
                response.append(c);
            }
            System.out.println("<<< " + response);
            Thread.sleep(100);  // 稍作延迟
        }

        socket.close();
    }
}