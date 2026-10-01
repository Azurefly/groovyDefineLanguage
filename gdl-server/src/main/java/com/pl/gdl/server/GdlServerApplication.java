package com.pl.gdl.server;

import com.pl.gdl.server.config.ServerConfig;
import com.pl.gdl.server.http.GdlHttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GdlServerApplication {
    private static final Logger log = LoggerFactory.getLogger(GdlServerApplication.class);

    public static void main(String[] args) {
        int port = 8080;
        String token = null;

        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if ("--token".equals(args[i]) && i + 1 < args.length) {
                token = args[++i];
            } else if ("--help".equals(args[i])) {
                System.out.println("GDL / TRE Engine Server");
                System.out.println("Usage: java -cp ... com.pl.gdl.server.GdlServerApplication [options]");
                System.out.println("Options:");
                System.out.println("  --port <port>    Listen port (default 8080)");
                System.out.println("  --token <token>  Security token for tre-token verification");
                System.out.println("  --help           Print help");
                return;
            }
        }

        ServerConfig config = new ServerConfig(port, token);
        GdlHttpServer server = new GdlHttpServer(config);

        try {
            server.start();
            log.info("=============================================================");
            log.info(" GDL Engine Remote Service running at http://localhost:{}", server.getPort());
            log.info(" HTTP REST API: http://localhost:{}/tre/api/...", server.getPort());
            log.info(" MCP Tool API:  http://localhost:{}/tre/mcp/service", server.getPort());
            log.info(" Health Check:  http://localhost:{}/tre/api/health", server.getPort());
            log.info(" Token Auth:    {}", config.isRequireToken() ? "ENABLED" : "DISABLED");
            log.info("=============================================================");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down GDL Engine Server...");
                server.stop();
            }));

            // Block main thread to keep server alive
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("Fatal: failed to start server", e);
            System.exit(1);
        }
    }
}
