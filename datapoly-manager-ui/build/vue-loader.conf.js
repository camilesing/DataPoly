'use strict'
const utils = require('./utils')
const config = require('../config')
const isProduction = process.env.NODE_ENV === 'production'
const sourceMapEnabled = isProduction
  ? config.build.productionSourceMap
  : config.dev.cssSourceMap

// Babel options resolved to absolute paths (require.resolve) so the same config
// works for files living outside this project's tree — specifically the UI
// extension sources bundled through the '@extension' alias (babel 6 resolves
// plugin names relative to the file being compiled, not to the config location).
const babelOptions = {
  babelrc: false,
  presets: [
    [require.resolve('babel-preset-env'), {
      modules: false,
      targets: {
        browsers: [
          '> 1%',
          'last 2 versions',
          'not ie <= 8'
        ]
      }
    }],
    require.resolve('babel-preset-stage-2')
  ],
  plugins: [
    require.resolve('babel-plugin-transform-vue-jsx'),
    require.resolve('babel-plugin-transform-runtime'),
    require.resolve('babel-plugin-syntax-dynamic-import')
  ]
}

module.exports = {
  loaders: Object.assign(utils.cssLoaders({
    sourceMap: sourceMapEnabled,
    extract: isProduction
  }), {
    js: { loader: 'babel-loader', options: babelOptions }
  }),
  babelOptions: babelOptions,
  cssSourceMap: sourceMapEnabled,
  cacheBusting: config.dev.cacheBusting,
  transformToRequire: {
    video: ['src', 'poster'],
    source: 'src',
    img: 'src',
    image: 'xlink:href'
  }
}