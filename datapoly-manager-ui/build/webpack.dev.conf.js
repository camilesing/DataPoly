'use strict'
const path = require('path')
const webpack = require('webpack')
const { merge } = require('webpack-merge')
const config = require('../config')
const utils = require('./utils')
const baseWebpackConfig = require('./webpack.base.conf')
const HtmlWebpackPlugin = require('html-webpack-plugin')
const CopyWebpackPlugin = require('copy-webpack-plugin')
const portfinder = require('portfinder')

const HOST = process.env.HOST
const PORT = process.env.PORT && Number(process.env.PORT)

// webpack-dev-server 5 takes the proxy as an array; keep the classic object form
// ({ '/api': { target, ... } } or { '/api': 'http://host' }) in config/index.js
function toProxyList (proxyTable) {
  return Object.keys(proxyTable).map(context => {
    const entry = proxyTable[context]
    return typeof entry === 'string' ? { context, target: entry } : Object.assign({ context }, entry)
  })
}

const devWebpackConfig = merge(baseWebpackConfig, {
  mode: 'development',
  module: {
    rules: utils.styleLoaders({ sourceMap: config.dev.cssSourceMap, usePostCSS: true })
  },
  // fastest rebuild-capable source map under webpack 5 naming
  devtool: config.dev.devtool,

  // these devServer options should be customized in /config/index.js
  devServer: {
    historyApiFallback: {
      rewrites: [
        { from: /.*/, to: path.posix.join(config.dev.assetsPublicPath, 'index.html') },
      ],
    },
    hot: true,
    compress: true,
    host: HOST || config.dev.host,
    port: PORT || config.dev.port,
    open: config.dev.autoOpenBrowser,
    // static assets are already copied by CopyWebpackPlugin; no extra static root
    static: false,
    client: {
      overlay: config.dev.errorOverlay
        ? { warnings: false, errors: true }
        : false,
      logging: 'warn'
    },
    proxy: toProxyList(config.dev.proxyTable).length > 0 ? toProxyList(config.dev.proxyTable) : undefined
    // (config.dev.poll watcher tuning was dropped with webpack-dev-server 5's
    // watchOptions removal; set devServer.watchFiles.poll here if ever needed)
  },
  plugins: [
    new webpack.DefinePlugin({
      'process.env': require('../config/dev.env')
    }),
    new HtmlWebpackPlugin({
      filename: 'index.html',
      template: 'index.html',
      inject: true
    }),
    // copy custom static assets
    new CopyWebpackPlugin({
      patterns: [
        {
          from: path.resolve(__dirname, '../static'),
          to: config.dev.assetsSubDirectory,
          globOptions: { ignore: ['**/.*'] },
          // static/ ships as an empty placeholder (.gitkeep only); tolerate that
          noErrorOnMissing: true
        }
      ]
    })
  ],
  optimization: {
    // HMR console shows readable module names; failed compiles emit nothing
    moduleIds: 'named',
    emitOnErrors: false
  }
})

module.exports = new Promise((resolve, reject) => {
  portfinder.basePort = process.env.PORT || config.dev.port
  portfinder.getPort((err, port) => {
    if (err) {
      reject(err)
    } else {
      // publish the new Port, necessary for e2e tests
      process.env.PORT = port
      // add port to devServer config
      devWebpackConfig.devServer.port = port
      resolve(devWebpackConfig)
    }
  })
})
