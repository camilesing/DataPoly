'use strict'

process.env.NODE_ENV = 'production'

const fs = require('fs')
const path = require('path')
const chalk = require('chalk')
const webpack = require('webpack')
const config = require('../config')
const webpackConfig = require('./webpack.prod.conf')

const isDebugBuild = process.env.DATAPOLY_UI_ENV === 'debug'
console.log(isDebugBuild
  ? 'building debug bundle (devtools enabled, do not ship)...'
  : 'building for production...')

fs.rmSync(path.join(config.build.assetsRoot, config.build.assetsSubDirectory), {
  recursive: true,
  force: true
})

webpack(webpackConfig, (err, stats) => {
  if (err) throw err
  process.stdout.write(stats.toString({
    colors: true,
    modules: false,
    children: false, // If you are using ts-loader, setting this to true will make TypeScript errors show up during build.
    chunks: false,
    chunkModules: false
  }) + '\n\n')

  if (stats.hasErrors()) {
    console.log(chalk.red('  Build failed with errors.\n'))
    process.exit(1)
  }

  console.log(chalk.cyan('  Build complete.\n'))
  console.log(chalk.yellow(
    '  Tip: built files are meant to be served over an HTTP server.\n' +
    '  Opening index.html over file:// won\'t work.\n'
  ))
})
