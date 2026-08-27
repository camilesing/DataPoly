<template>
  <div>
    <el-card>
      <el-form label-width="200px">
        <el-form-item :label="$t('setting.accessControl')">
          <el-switch v-model="status"
                     active-color="#13ce66"
                     active-value="ON"
                     inactive-value="OFF"
                     :active-text="$t('setting.open')"
                     :inactive-text="$t('setting.closeText')">
          </el-switch>
        </el-form-item>
        <div v-show="status=='ON'">
          <el-form-item :label="$t('setting.list')">
            <el-radio-group v-model="mode"
                            @change="modeChange">
              <el-radio label="BLACK">{{ $t('setting.blackList') }}</el-radio>
              <el-radio label="WHITE">{{ $t('setting.whiteList') }}</el-radio>
            </el-radio-group>
            <el-alert :title="$t('setting.blackListTip')"
                      type="warning"
                      :closable="false"
                      v-show="mode == 'BLACK'"></el-alert>
            <el-alert :title="$t('setting.whiteListTip')"
                      type="warning"
                      :closable="false"
                      v-show="mode == 'WHITE'"></el-alert>
          </el-form-item>
          <el-form-item :label="$t('setting.blackListIp')"
                        v-show="mode == 'BLACK'">
            <el-input type="textarea"
                      :autosize="{ minRows: 8, maxRows: 20 }"
                      v-model="addresses"
                      :placeholder="$t('setting.ipPlaceholder')">
            </el-input>
          </el-form-item>
          <el-form-item :label="$t('setting.whiteListIp')"
                        v-show="mode == 'WHITE'">
            <el-input type="textarea"
                      :autosize="{ minRows: 8, maxRows: 20 }"
                      v-model="addresses"
                      :placeholder="$t('setting.ipPlaceholder')">
            </el-input>
          </el-form-item>
        </div>
        <el-form-item>
          <el-button type="primary"
                     @click="handleSave"
                     plain>{{ $t('setting.saveBtn') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script>

export default {
  name: "firewall",
  data () {
    return {
      status: "ON",
      mode: "BLACK",
      addresses: ""
    }
  },
  methods: {
    loadData: function () {
      this.$http.get("/datapoly/manager/api/v1/firewall/detail").then(res => {
        if (0 === res.data.code) {
          this.status = res.data.data.status;
          this.mode = res.data.data.mode;
          this.addresses = res.data.data.addresses;
        } else {
          alert(this.$t('setting.loadFailed') + res.data.message);
        }
      }
      );
    },
    modeChange (p) {
      console.log(p)
    },
    handleSave () {
      if (this.status === 'ON' && (!this.addresses || /^\s*$/.test(this.addresses))) {
        alert(this.$t('setting.ipListEmpty'))
        return
      }
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/firewall/save",
        data: JSON.stringify({
          status: this.status,
          mode: this.mode,
          addresses: this.addresses
        })
      }).then(res => {
        if (0 === res.data.code) {
          this.$alert(this.$t('setting.saveSuccessTip'), this.$t('common.info'),
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
.el-table {
  width: 100%;
  height: 100%;
}
.el-card,
.el-message {
  width: 100%;
  height: 100%;
  overflow: auto;
}
</style>
