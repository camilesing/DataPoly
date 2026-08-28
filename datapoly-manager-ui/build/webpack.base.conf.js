'use strict'
const path = require('path')
const fs = require('fs')
const utils = require('./utils')
const config = require('../config')
const vueLoaderConfig = require('./vue-loader.conf')

function resolve (dir) {
  return path.join(__dirname, '..', dir)
}

// Compile-time assembly of the local UI extension (../datapoly-extension-ui, gitignored).
// When its entry file exists, its sources are bundled through the '@extension' alias and
// transpiled by babel; otherwise the in-repo stub below keeps an empty extension, so
// builds without the extension stay byte-for-byte unaffected.
const extensionUiSrc = path.resolve(__dirname, '../../datapoly-extension-ui/src')
const extensionUiEntry = path.join(extensionUiSrc, 'index.js')
const hasExtensionUi = fs.existsSync(extensionUiEntry)



module.exports = {
  context: path.resolve(__dirname, '../'),
  entry: {
    app: './src/main.js'
  },
  output: {
    path: config.build.assetsRoot,
    filename: '[name].js',
    publicPath: process.env.NODE_ENV === 'production'
      ? config.build.assetsPublicPath
      : config.dev.assetsPublicPath
  },
  resolve: {
    extensions: ['.js', '.vue', '.json'],
    // Extension sources sit outside the manager-ui tree, so its npm imports cannot
    // rely on the classic upward node_modules lookup. Its own node_modules (if any, the
    // recommended pin-point for extension-specific deps) and manager-ui's node_modules
    // are searched explicitly; extension authors must not install vue/element-ui there.
    modules: (hasExtensionUi ? [path.join(path.dirname(extensionUiEntry), 'node_modules')] : [])
      .concat(['node_modules', resolve('node_modules')]),
    alias: {
      'vue$': 'vue/dist/vue.esm.js',
      '@': resolve('src'),
      '@extension': hasExtensionUi ? extensionUiSrc : resolve('src/extension-stub')
    }
  },
  resolveLoader: {
    // Loaders injected by vue-loader (vue-style-loader, css-loader, ...) for
    // extension SFCs resolve relative to the .vue file just like babel plugins;
    // pin the search to this project's node_modules so out-of-tree sources work.
    modules: ['node_modules', resolve('node_modules')]
  },
  module: {
    rules: [
      {
        test: /\.vue$/,
        loader: 'vue-loader',
        options: vueLoaderConfig
      },
      {
        test: /\.js$/,
        loader: 'babel-loader',
        options: vueLoaderConfig.babelOptions,
        include: [resolve('src'), resolve('test'), resolve('node_modules/webpack-dev-server/client')].concat(
          hasExtensionUi ? [extensionUiSrc] : []
        )
      },
      {
        test: /\.(png|jpe?g|gif|svg)(\?.*)?$/,
        loader: 'url-loader',
        options: {
          limit: 10000,
          name: utils.assetsPath('img/[name].[hash:7].[ext]')
        }
      },
      {
        test: /\.(mp4|webm|ogg|mp3|wav|flac|aac)(\?.*)?$/,
        loader: 'url-loader',
        options: {
          limit: 10000,
          name: utils.assetsPath('media/[name].[hash:7].[ext]')
        }
      },
      {
        test: /\.(woff2?|eot|ttf|otf)(\?.*)?$/,
        loader: 'url-loader',
        options: {
          limit: 10000,
          name: utils.assetsPath('fonts/[name].[hash:7].[ext]')
        }
      }
    ]
  },
  node: {
    // prevent webpack from injecting useless setImmediate polyfill because Vue
    // source contains it (although only uses it if it's native).
    setImmediate: false,
    // prevent webpack from injecting mocks to Node native modules
    // that does not make sense for the client
    dgram: 'empty',
    fs: 'empty',
    net: 'empty',
    tls: 'empty',
    child_process: 'empty'
  }
}
