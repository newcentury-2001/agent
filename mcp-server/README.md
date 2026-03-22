# MCP Server (Java)

Minimal MCP SSE server (pure Java, JDK 17) that wraps Aliyun China weather API as a tool.

## Quick Start

1. Set env:

```
set ALIYUN_APPCODE=YOUR_APPCODE
```

Optional:

```
set MCP_HOST=0.0.0.0
set MCP_PORT=8086
set MCP_SSE_PATH=/mcp/sse
set MCP_MESSAGES_PATH=/mcp/messages
set WEATHER_HOST=https://tinaqi.market.alicloudapi.com
set WEATHER_PATH=/area-to-weather-date
```

2. Build:

```
mvn -q -DskipTests package
```

3. Run:

```
java -jar target/mcp-server-0.1.0.jar
```

## MCP SSE URL

```
http://<host>:<port>/mcp/sse
```

## Tool Provided

Tool name: `china_weather`

Arguments:

- `areaCode` (required)
- `area` (required)
- `date` (required, `YYYYMMDD`)
- `need3HourForcast` (optional, `1` / `0`)

## Tool Market Install Command (SSE)

```
{
  "mcpServers": {
    "china-weather": {
      "url": "http://<host>:<port>/mcp/sse"
    }
  }
}
```

This server reads `ALIYUN_APPCODE` from environment, so no headers are required.
