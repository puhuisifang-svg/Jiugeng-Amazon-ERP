# Secrets 目录说明

本目录**禁止保存任何真实 Token、Key、密码、私钥、证书或凭据文件**。

真实凭据存放在受保护的 Secret Store、云 Secret Manager、CI/CD 受保护变量或经批准的系统钥匙串中。本仓库仅维护：

- [密钥管理规范](../docs/05-security/SECRETS_MANAGEMENT.md)
- [密钥登记表](../docs/05-security/SECRET_REGISTRY.md)

如果发现真实凭据进入本目录或 Git 历史，应立即停止传播、吊销/轮换凭据，并按泄露响应流程处理。
