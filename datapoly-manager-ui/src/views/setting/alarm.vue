<template>
  <div>
    <el-card>
      <el-form label-width="200px">
        <el-form-item :label="$t('setting.alarmConfig')">
          <el-switch v-model="status"
                     active-color="#13ce66"
                     active-value="ON"
                     inactive-value="OFF"
                     :active-text="$t('setting.open')"
                     :inactive-text="$t('setting.closeText')">
          </el-switch>
        </el-form-item>
        <div v-show="status=='ON'">
          <el-form-item :label="$t('setting.endpoint')">
            <span slot="label"
                  style="display:inline-block;">
              {{ $t('setting.endpoint') }}
              <el-tooltip effect="dark"
                          :content="$t('setting.endpointTip')"
                          placement="bottom">
                <i class='el-icon-question' />
              </el-tooltip>
            </span>
            <el-input v-model="endpoint"
                      placeholder="http://127.0.0.1:8000/api/v1/message/send">
            </el-input>
          </el-form-item>
          <el-form-item :label="$t('setting.inputFormat')">
            <span slot="label"
                  style="display:inline-block;">
              {{ $t('setting.inputFormat') }}
              <el-tooltip effect="dark"
                          :content="$t('setting.inputFormatTip')"
                          placement="bottom">
                <i class='el-icon-question' />
              </el-tooltip>
            </span>
            <el-select v-model="contentType">
              <el-option label="application/json"
                         value="application/json"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('setting.inputTemplate')">
            <span slot="label"
                  style="display:inline-block;">
              {{ $t('setting.inputTemplate') }}
              <el-tooltip effect="dark"
                          :content="$t('setting.inputTemplateTip')"
                          placement="bottom">
                <i class='el-icon-question' />
              </el-tooltip>
            </span>
            <el-input type="textarea"
                      :autosize="{ minRows: 8, maxRows: 20 }"
                      v-model="inputTemplate"
                      :placeholder="$t('setting.inputTemplatePlaceholder')">
            </el-input>
          </el-form-item>
        </div>
        <el-form-item>
          <el-button type="primary"
                     v-show="status=='ON'"
                     @click="handleShowTest"
                     plain>{{ $t('setting.test') }}</el-button>
          <el-button type="primary"
                     @click="handleSave"
                     plain>{{ $t('setting.save') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-dialog :title="$t('setting.testAlarmConfig')"
               :visible.sync="testFormVisible"
               :showClose="false"
               :before-close="handleClose">
      <el-form label-width="200px">
        <el-form-item v-for="item in dataModel"
                      :key="item.key"
                      v-bind="item"
                      :label="`\$\{${item.key}\}`">
          <el-input type="text"
                    :key="item.key"
                    v-model="item.value"
                    :value="item.value"> </el-input>
        </el-form-item>
      </el-form>
      <div slot="footer"
           class="dialog-footer">
        <el-button type="primary"
                   @click="handleSendTest"
                   plain>{{ $t('setting.sendTest') }}</el-button>
        <el-button type="primary"
                   @click="testFormVisible = false"
                   plain>{{ $t('setting.close') }}</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>

export default {
  name: "alarm",
  data () {
    return {
      status: "ON",
      endpoint: "",
      contentType: "application/json",
      inputTemplate: "",
      testFormVisible: false,
      dataModel: {},
    }
  },
  methods: {
    loadData: function () {
      this.$http.get("/datapoly/manager/api/v1/alarm/detail").then(res => {
        if (0 === res.data.code) {
          this.status = res.data.data.status;
          this.endpoint = res.data.data.endpoint;
          this.contentType = res.data.data.contentType;
          this.inputTemplate = res.data.data.inputTemplate;
        } else {
          alert(this.$t('setting.loadFailed') + res.data.message);
        }
      }
      );
    },
    handleShowTest () {
      this.$http.get("/datapoly/manager/api/v1/alarm/example").then(res => {
        if (0 === res.data.code) {
          this.dataModel = res.data.data;
          this.testFormVisible = true;
        } else {
          alert(this.$t('setting.loadFailed') + res.data.message);
          return;
        }
      }
      );
    },
    handleClose (done) {
    },
    handleSendTest () {
      if (this.status === 'ON' && (!this.endpoint || /^\s*$/.test(this.endpoint))) {
        alert(this.$t('setting.endpointEmpty'))
        return
      }
      if (this.status === 'ON' && (!this.contentType || /^\s*$/.test(this.contentType))) {
        alert(this.$t('setting.inputTypeRequired'))
        return
      }
      if (this.status === 'ON' && (!this.inputTemplate || /^\s*$/.test(this.inputTemplate))) {
        alert(this.$t('setting.templateRequired'))
        return
      }
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/alarm/test",
        data: JSON.stringify({
          endpoint: this.endpoint,
          contentType: this.contentType,
          inputTemplate: this.inputTemplate,
          dataModel: this.dataModel
        })
      }).then(res => {
        if (0 === res.data.code) {
          this.$alert(this.$t('setting.sendTestSuccess'), this.$t('common.info'),
            {
              confirmButtonText: this.$t('setting.confirm'),
              type: "info"
            }
          );
        } else {
          this.$alert(res.data.message, this.$t('common.info'),
            {
              confirmButtonText: this.$t('setting.confirm'),
              type: "error"
            }
          );
        }
      });
    },
    handleSave () {
      if (this.status === 'ON' && (!this.endpoint || /^\s*$/.test(this.endpoint))) {
        alert(this.$t('setting.endpointEmpty'))
        return
      }
      if (this.status === 'ON' && (!this.contentType || /^\s*$/.test(this.contentType))) {
        alert(this.$t('setting.inputTypeRequired'))
        return
      }
      if (this.status === 'ON' && (!this.inputTemplate || /^\s*$/.test(this.inputTemplate))) {
        alert(this.$t('setting.templateRequired'))
        return
      }
      this.$http({
        method: "POST",
        headers: {
          'Content-Type': 'application/json'
        },
        url: "/datapoly/manager/api/v1/alarm/save",
        data: JSON.stringify({
          status: this.status,
          endpoint: this.endpoint,
          contentType: this.contentType,
          inputTemplate: this.inputTemplate
        })
      }).then(res => {
        if (0 === res.data.code) {
          this.$alert(this.$t('setting.saveSuccess'), this.$t('common.info'),
            {
              confirmButtonText: this.$t('setting.confirm'),
              type: "info"
            }
          );
          this.loadData();
        } else {
          alert(this.$t('message.operationFailed') + ":" + res.data.message);
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
