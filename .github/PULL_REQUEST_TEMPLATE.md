## Summary

<!-- What does this PR change and why? 简述本 PR 的改动内容与动机 -->

## Type of change

- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing behavior to change)
- [ ] Documentation / build / CI only

## Checklist

- [ ] `mvn test` passes locally (`mvn -B -ntp test -pl datapoly-common,datapoly-template,datapoly-core,datapoly-executor,datapoly-gateway,datapoly-manager -am`)
- [ ] If the built-in UI (`datapoly-manager-ui`) was changed, the dist assets were rebuilt and synced into `datapoly-manager/src/main/resources/`
- [ ] No new hardcoded credentials, internal IPs or personal namespaces were introduced
