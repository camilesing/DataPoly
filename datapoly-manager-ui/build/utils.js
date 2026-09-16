'use strict'
const path = require('path')
const MiniCssExtractPlugin = require('mini-css-extract-plugin')
const config = require('../config')

exports.assetsPath = function (_path) {
  const assetsSubDirectory = process.env.NODE_ENV === 'production'
    ? config.build.assetsSubDirectory
    : config.dev.assetsSubDirectory

  return path.posix.join(assetsSubDirectory, _path)
}

exports.cssLoaders = function (options) {
  options = options || {}

  const cssLoader = {
    loader: 'css-loader',
    options: {
      sourceMap: options.sourceMap,
      // vue-style-loader expects the CSS export to be a string module;
      // css-loader 6+ defaults to ES modules which breaks it
      esModule: false
    }
  }

  const postcssLoader = {
    loader: 'postcss-loader',
    options: {
      sourceMap: options.sourceMap,
      postcssOptions: {
        // explicitly point at the project's .postcssrc.js so extension sources
        // compiled from outside the tree pick up the same plugins
        config: path.resolve(__dirname, '../.postcssrc.js')
      }
    }
  }

  // generate loader chain for one style language
  function generateLoaders (loader, loaderOptions) {
    const loaders = options.usePostCSS ? [cssLoader, postcssLoader] : [cssLoader]

    if (loader) {
      const loaderEntry = { loader: loader + '-loader' }
      // less-loader 9+ no longer accepts a top-level sourceMap key
      if (loaderOptions && Object.keys(loaderOptions).length > 0) {
        loaderEntry.options = Object.assign({}, loaderOptions)
      }
      loaders.push(loaderEntry)
    }

    // Extract CSS into chunk files on production builds; dev keeps HMR-friendly
    // vue-style-loader injection
    if (options.extract) {
      return [MiniCssExtractPlugin.loader].concat(loaders)
    }
    return ['vue-style-loader'].concat(loaders)
  }

  // https://vue-loader.vuejs.org
  return {
    css: generateLoaders(),
    postcss: generateLoaders(),
    less: generateLoaders('less')
  }
}

// Generate loaders for standalone style files (outside of .vue)
exports.styleLoaders = function (options) {
  const output = []
  const loaders = exports.cssLoaders(options)

  for (const extension in loaders) {
    const loader = loaders[extension]
    output.push({
      test: new RegExp('\\.' + extension + '$'),
      use: loader
    })
  }

  return output
}
