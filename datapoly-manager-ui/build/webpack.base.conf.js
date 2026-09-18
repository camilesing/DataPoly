'use strict'
const path = require('path')
const webpack = require('webpack')
const fs = require('fs')
const utils = require('./utils')
const config = require('../config')
const vueLoaderConfig = require('./vue-loader.conf')
const { VueLoaderPlugin } = require('vue-loader')

function resolve (dir) {
  return path.join(__dirname, '..', dir)
}

// Compile-time assembly of the local UI extension (../../datapoly-extension/front/src, gitignored).
// When its entry file exists, its sources are bundled through the '@extension' alias and
// transpiled by babel; otherwise the in-repo stub below keeps an empty extension, so
// builds without the extension stay byte-for-byte unaffected.
const extensionUiSrc = path.resolve(__dirname, '../../datapoly-extension/front/src')
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
    // webpack 5 dropped the automatic node polyfills this app relied on through
    // urlencode -> iconv-lite; buffer and string_decoder are the ones actually needed
    // (iconv-lite >=0.5 additionally pulls string_decoder for its codecs)
    fallback: {
      buffer: require.resolve('buffer/'),
      'string_decoder': require.resolve('string_decoder/')
    },
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
        options: vueLoaderConfig.vueOptions
      },
      {
        test: /\.js$/,
        loader: 'babel-loader',
        options: vueLoaderConfig.babelOptions,
        include: [resolve('src'), resolve('test')].concat(
          hasExtensionUi ? [extensionUiSrc] : []
        )
      },
      // webpack 5 asset modules replace url-loader/file-loader: assets under 10KB
      // are inlined, the rest are emitted with a stable content hash
      {
        test: /\.(png|jpe?g|gif|svg)(\?.*)?$/,
        type: 'asset',
        parser: { dataUrlCondition: { maxSize: 10 * 1024 } },
        generator: { filename: utils.assetsPath('img/[name].[hash:7][ext]') }
      },
      {
        test: /\.(mp4|webm|ogg|mp3|wav|flac|aac)(\?.*)?$/,
        type: 'asset',
        parser: { dataUrlCondition: { maxSize: 10 * 1024 } },
        generator: { filename: utils.assetsPath('media/[name].[hash:7][ext]') }
      },
      {
        test: /\.(woff2?|eot|ttf|otf)(\?.*)?$/,
        type: 'asset',
        parser: { dataUrlCondition: { maxSize: 10 * 1024 } },
        generator: { filename: utils.assetsPath('fonts/[name].[hash:7][ext]') }
      }
    ]
  },
  plugins: [
    // required by vue-loader 15 to bridge SFC blocks into the rules above
    new VueLoaderPlugin(),
    // global Buffer for urlencode -> iconv-lite
    new webpack.ProvidePlugin({ Buffer: ['buffer', 'Buffer'] })
  ]
}
