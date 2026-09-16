'use strict'
const config = require('../config')

const isProduction = process.env.NODE_ENV === 'production'
const sourceMapEnabled = isProduction
  ? config.build.productionSourceMap
  : config.dev.cssSourceMap

// Babel options resolved to absolute paths (require.resolve) so the same config
// works for files living outside this project's tree — specifically the UI
// extension sources bundled through the '@extension' alias (babel resolves
// plugin/preset names relative to the config location; absolute paths sidestep that).
// babelrc/configFile are disabled to keep the build hermetic for out-of-tree files.
const babelOptions = {
  babelrc: false,
  configFile: false,
  presets: [
    [require.resolve('@babel/preset-env'), {
      modules: false,
      targets: {
        browsers: [
          '> 1%',
          'last 2 versions',
          'not ie <= 8'
        ]
      }
    }],
    // Vue 2 JSX (renderContent(h, ...) trees in src/views/interface/common.vue etc.)
    require.resolve('@vue/babel-preset-jsx')
  ],
  plugins: [
    require.resolve('@babel/plugin-transform-runtime')
  ]
}

module.exports = {
  // vue-loader 15 resolves SFC sub-resources through the regular webpack rules
  // (the old per-block `loaders` override is gone); these are the remaining
  // vue-loader options
  vueOptions: {
    cssSourceMap: sourceMapEnabled,
    transformAssetUrls: {
      video: ['src', 'poster'],
      source: 'src',
      img: 'src',
      image: 'xlink:href'
    }
  },
  babelOptions: babelOptions
}
