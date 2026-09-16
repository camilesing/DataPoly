'use strict'
const path = require('path')
const utils = require('./utils')
const webpack = require('webpack')
const config = require('../config')
const { merge } = require('webpack-merge')
const baseWebpackConfig = require('./webpack.base.conf')
const CopyWebpackPlugin = require('copy-webpack-plugin')
const HtmlWebpackPlugin = require('html-webpack-plugin')
const MiniCssExtractPlugin = require('mini-css-extract-plugin')
const TerserPlugin = require('terser-webpack-plugin')
const CssMinimizerPlugin = require('css-minimizer-webpack-plugin')

// DATAPOLY_UI_ENV=debug keeps NODE_ENV at "development" so the Vue devtools
// hook survives (it is stripped when NODE_ENV is replaced with "production").
// Debug builds are for local debugging only and must never be shipped.
const isDebugBuild = process.env.DATAPOLY_UI_ENV === 'debug'
const env = Object.assign({}, require('../config/prod.env'), {
  NODE_ENV: JSON.stringify(isDebugBuild ? 'development' : 'production')
})

const webpackConfig = merge(baseWebpackConfig, {
  mode: 'production',
  module: {
    rules: utils.styleLoaders({
      sourceMap: config.build.productionSourceMap,
      extract: true,
      usePostCSS: true
    })
  },
  devtool: config.build.productionSourceMap ? config.build.devtool : false,
  output: {
    path: config.build.assetsRoot,
    filename: utils.assetsPath('js/[name].[contenthash].js'),
    chunkFilename: utils.assetsPath('js/[id].[contenthash].js')
  },
  optimization: {
    // stable module ids keep the vendor chunk hash from churning on app-only changes
    moduleIds: 'deterministic',
    // extract the webpack runtime into "manifest" so vendor stays cacheable
    runtimeChunk: { name: 'manifest' },
    splitChunks: {
      cacheGroups: {
        // all node_modules code reachable from initial chunks -> vendor bundle
        vendor: {
          name: 'vendor',
          test: /[\\/]node_modules[\\/]/,
          chunks: 'initial',
          priority: -10
        },
        // node_modules code shared by 3+ async chunks -> shared async bundle
        'vendor-async': {
          name: 'vendor-async',
          test: /[\\/]node_modules[\\/]/,
          chunks: 'async',
          minChunks: 3,
          priority: -20,
          reuseExistingChunk: true
        }
      }
    },
    minimizer: [
      new TerserPlugin({
        parallel: true
        // source maps follow the top-level devtool automatically (the old
        // `sourceMap` option was removed in terser-webpack-plugin 5.3.2+)
      }),
      // dedupes and minifies the extracted CSS (old optimize-css-assets role)
      new CssMinimizerPlugin({})
    ]
  },
  plugins: [
    new webpack.DefinePlugin({
      'process.env': env
    }),
    // extract css into its own file per chunk (covers async chunks too,
    // matching the old ExtractTextPlugin allChunks behavior)
    new MiniCssExtractPlugin({
      filename: utils.assetsPath('css/[name].[contenthash].css'),
      // element-ui import order legitimately differs across chunks
      ignoreOrder: true
    }),
    // generate dist index.html with correct asset hash for caching.
    // you can customize output by editing /index.html
    new HtmlWebpackPlugin({
      filename: config.build.index,
      template: 'index.html',
      inject: true,
      minify: {
        removeComments: true,
        collapseWhitespace: true,
        removeAttributeQuotes: true
        // more options:
        // https://github.com/kangax/html-minifier#options-quick-reference
      }
    }),
    // copy custom static assets
    new CopyWebpackPlugin({
      patterns: [
        {
          from: path.resolve(__dirname, '../static'),
          to: config.build.assetsSubDirectory,
          globOptions: { ignore: ['**/.*'] },
          // static/ ships as an empty placeholder (.gitkeep only); tolerate that
          noErrorOnMissing: true
        }
      ]
    })
  ]
})

if (config.build.bundleAnalyzerReport) {
  const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin
  webpackConfig.plugins.push(new BundleAnalyzerPlugin())
}

module.exports = webpackConfig
