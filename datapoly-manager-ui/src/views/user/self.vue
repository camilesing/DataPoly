<template>
  <div>
    <el-card class="box-card">
      <el-tabs v-model="activeName"
               @tab-click="handleClick">
        <image style="width: 100px; height: 100px"
               src="../../assets/logo.svg" />
        <el-tab-pane :label="$t('user.accountInfo')"
                     name="userinfo">
          <el-card>
            <el-description :title="$t('user.accountBasicInfo')">
              <el-description-item :label="$t('user.account')"
                                   :span='15'
                                   :value="userinfo.username"></el-description-item>
              <el-description-item :label="$t('user.name')"
                                   :span='15'
                                   :value="userinfo.realName"></el-description-item>
              <el-description-item :label="$t('user.email')"
                                   :span='15'
                                   :value="userinfo.email"></el-description-item>
              <el-description-item :label="$t('user.address')"
                                   :span='15'
                                   :value="userinfo.address"></el-description-item>
              <el-description-item :label="$t('user.locked')"
                                   :span='15'
                                   :value="userinfo.locked"></el-description-item>
              <el-description-item :label="$t('user.createTime')"
                                   :span='15'
                                   :value="userinfo.createTime"></el-description-item>
            </el-description>

          </el-card>
        </el-tab-pane>
        <el-tab-pane :label="$t('user.passwordModify')"
                     name="modifyPassword">
          <el-card>
            <ul>
              <li>
                <p class="desc">
                  {{ $t('user.modifyPassword') }}
                  <a href="#"
                     @click="showPassword=true">{{ $t('user.modifyPasswordBtn') }}</a>
                </p>
              </li>
            </ul>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog :visible.sync="showPassword"
               @close="clearPassword"
               :showClose="false"
               :title="$t('user.modifyMyPassword')"
               width="360px"
               :before-close="handleClose">
      <el-form :model="pwdModify"
               :rules="rules"
               label-width="80px"
               ref="modifyPwdForm">
        <el-form-item :minlength="6"
                      :label="$t('user.oldPassword')"
                      prop="password">
          <el-input show-password
                    v-model="pwdModify.password"></el-input>
        </el-form-item>
        <el-form-item :minlength="6"
                      :label="$t('user.newPassword')"
                      prop="newPassword">
          <el-input show-password
                    v-model="pwdModify.newPassword"></el-input>
        </el-form-item>
        <el-form-item :minlength="6"
                      :label="$t('user.confirmPassword')"
                      prop="confirmPassword">
          <el-input show-password
                    v-model="pwdModify.confirmPassword"></el-input>
        </el-form-item>
      </el-form>
      <div class="dialog-footer"
           slot="footer">
        <el-button @click="showPassword=false">{{ $t('user.cancel') }}</el-button>
        <el-button @click="savePassword"
                   type="primary">{{ $t('user.confirm') }}</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>

import qs from "qs";
import ElDescription from '@/components/description/Description'
import ElDescriptionItem from '@/components/description/DescriptionItem'

export default {
  name: "Person",
  components: { ElDescription, ElDescriptionItem },
  data () {
    return {
      userinfo: {
        id: 0,
        username: "admin",
        realName: "Administrator",
        email: "admin@datapoly.com",
        address: "",
        locked: false,
        createTime: "2021-07-19 20:26:06",
        updateTime: "2021-07-19 20:26:06"
      },
      activeName: "userinfo",
      showPassword: false,
      pwdModify: {},
      rules: {
        password: [
          { required: true, message: this.$t('user.passwordRequired'), trigger: "blur" },
          { min: 6, message: this.$t('user.passwordMinLength'), trigger: "blur" }
        ],
        newPassword: [
          { required: true, message: this.$t('user.newPasswordRequired'), trigger: "blur" },
          { min: 6, message: this.$t('user.passwordMinLength'), trigger: "blur" }
        ],
        confirmPassword: [
          { required: true, message: this.$t('user.confirmPasswordRequired'), trigger: "blur" },
          { min: 6, message: this.$t('user.passwordMinLength'), trigger: "blur" },
          {
            validator: (rule, value, callback) => {
              if (value !== this.pwdModify.newPassword) {
                callback(new Error(this.$t('user.passwordMismatch')));
              } else {
                callback();
              }
            },
            trigger: "blur"
          }
        ]
      }
    };
  },
  created () {
    this.loadData();
    console.log(this.userinfo);
  },
  methods: {
    loadData: function () {
      this.$http
        .get(
          "/datapoly/manager/api/v1/user/detail/name?username=" +
          window.sessionStorage.getItem("username")
        )
        .then(
          res => {
            if (0 === res.data.code) {
              this.userinfo = res.data.data;
            } else {
              alert(this.$t('user.loadDataFailed') + res.data.message);
            }
          },
          error => {
            this.$message({
              showClose: true,
              message: this.$t('user.dataLoadError'),
              type: "error"
            });
          }
        );
    },
    handleClose () { },
    savePassword () {
      this.$http({
        method: 'POST',
        url: '/datapoly/manager/api/v1/user/changePassword',
        data: qs.stringify({
          oldPassword: this.pwdModify.password,
          newPassword: this.pwdModify.newPassword
        }),
      }).then(res => {
        console.log(res);
        if (0 === res.data.code) {
          this.showPassword = false;
          this.$message.success(this.$t('user.modifyPasswordSuccess'));
        } else {
          this.showPassword = true;
          this.$message(res.data.message);
        }
      });
    },
    clearPassword () {
      this.pwdModify = {
        password: "",
        newPassword: "",
        confirmPassword: ""
      };
      this.$refs.modifyPwdForm.clearValidate();
    },
    handleClick () {

    }
  }
};
</script>

<style scoped>
.text {
  font-size: 14px;
}

.item {
  padding: 18px 0;
}

.box-card {
  width: 95%;
}

.my-label {
  background: #e1f3d8;
}

.my-content {
  background: #fde2e2;
}
</style>
