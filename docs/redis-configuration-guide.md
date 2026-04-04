# Redis Configuration Guide

This document explains where and how Redis is configured in the TradingTool-3 backend. 

## 1. Environment Variable Override (Recommended)
The application is designed specifically for modern cloud deployments (like Render or Oracle Cloud). The default and most secure way to configure Redis is by passing the `REDIS_URL` environment variable to the running process. 

The application automatically checks for this environment variable first.

* **Upstash Redis:** `REDIS_URL=rediss://default:<password>@<endpoint>:<port>` (Note the `rediss://` for TLS/SSL).
* **Oracle Cloud / Local:** `REDIS_URL=redis://<host>:<port>`

## 2. Configuration Files

If you prefer to hardcode the configuration (e.g., for local testing), the setting has to be updated in the Dropwizard YAML configuration files. I have just added the `redis` block to the system so it can be parsed natively.

### A. `localconfig.yaml`
**Path:** `service/src/main/resources/localconfig.yaml`
Used when running the server locally on your Mac.

```yaml
redis:
  url: "redis://localhost:6379" # Replace with your Upstash/Oracle URL if you want to test remote locally
```

### B. `serverConfig.yml`
**Path:** `service/src/main/resources/serverConfig.yml`
Used when deploying to Render or Oracle Cloud. It automatically injects the `REDIS_URL` environment variable if it exists, otherwise it falls back to localhost.

```yaml
redis:
  url: ${REDIS_URL:-redis://localhost:6379}
```

## Setup Instructions for Upstash (Free Tier)
1. Go to your Upstash console and create a Redis database.
2. Scroll down to the **Connect to your database** section.
3. Select "Jedis" or "URL" to copy the connection string.
4. It will look like: `rediss://default:xxxxxxxxxxxxxx@us1-cool-name-34133.upstash.io:34133`
5. On Render (or Oracle Cloud), inject this string as the `REDIS_URL` environment variable.

*Note for Render:* Render environment variables automatically apply to your web service on the next deployment, which will seamlessly connect the `RedisHandler` Jedis pool to Upstash!
