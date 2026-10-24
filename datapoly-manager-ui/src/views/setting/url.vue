<template>
  <div>
    <el-card>
      <el-form label-width="200px">
        <el-form-item>
          <span slot="label"
                style="display:inline-block;">
            {{ $t('setting.gatewayUrl') }}
            <el-tooltip effect="dark"
                        :content="$t('setting.gatewayUrlTip')"
                        placement="bottom">
              <i class='el-icon-question' />
            </el-tooltip>
          </span>
          <el-input v-model="gatewayUrl"
                    placeholder="http://www.example.com:8091">
          </el-input>
          <div class="effective-line">{{ $t('setting.effectiveAddr') }}: {{ effectiveText(gateway) }}</div>
        </el-form-item>
        <el-form-item>
          <span slot="label"
                style="display:inline-block;">
            {{ $t('setting.managerUrl') }}
            <el-tooltip effect="dark"
                        :content="$t('setting.managerUrlTip')"
                        placement="bottom">
              <i class='el-icon-question' />
            </el-tooltip>
          </span>
          <el-input v-model="managerUrl"
                    placeholder="http://www.example.com:8090">
          </el-input>
          <div class="effective-line">{{ $t('setting.effectiveAddr') }}: {{ effectiveText(manager) }}</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary"
                     @click="handleSave"
                     plain>{{ $t('setting.save') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script>

export default {
  name: "url",
  data () {
    return {
      gatewayUrl: "",
      managerUrl: "",
      gateway: {},
      manager: {}
    }
  },
  methods: {
    sourceText (source) {
      const keyMap = {
        PARAM: "setting.sourceParam",
        ENV: "setting.sourceEnv",
        DISCOVERY: "setting.sourceDiscovery"
      };
      return this.$t(keyMap[source] || "setting.sourceNone");
    },
    effectiveText (item) {
      if (!item || !item.effective) {
        return this.$t("setting.sourceNone");
      }
      return item.effective + " (" + this.sourceText(item.source) + ")";
    },
    loadData: function () {
      this.$http.get("/datapoly/manager/api/v1/node/url-config").then(res => {
        if (0 === res.data.code) {
          this.gateway = res.data.data.gateway || {};
          this.manager = res.data.data.manager || {};
          this.gatewayUrl = this.gateway.value || "";
          this.managerUrl = this.manager.value || "";
        } else {
          alert(this.$t('setting.loadFailed') + res.data.message);
        }
      }
      );
    },
    handleSave () {
      this.$http({
        method: "POST",
        url: "/datapoly/manager/api/v1/node/url-config",
        params: {
          gatewayUrl: this.gatewayUrl || "",
          managerUrl: this.managerUrl || ""
        }
      }).then(res => {
        if (0 === res.data.code) {
          this.$alert(this.$t('setting.urlSaveSuccess'), this.$t('common.info'),
            {
              confirmButtonText: this.$t('common.confirm'),
              type: "info"
            }
          );
          this.loadData();
        } else {
          alert(this.$t('setting.saveFailed') + res.data.message);
        }
      });
    }
  },
  created () {
    this.loadData();
  }
};
</script>

<style scoped>
.el-card,
.el-message {
  width: 100%;
  height: 100%;
  overflow: auto;
}

.effective-line {
  font-size: 12px;
  color: var(--dp-text-muted);
  line-height: 20px;
  padding-top: 4px;
}
</style>