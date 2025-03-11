const path = require('path');
const webpack = require('webpack');

module.exports = {
  mode: "development",
  entry: {
    main : "./target/main.js",
    workers : "./target/workers.js"
  },

  externals: {
    'react': 'React',
    'react-dom': 'ReactDOM',
  },

  output: {
    path: path.resolve(__dirname, 'static/js'),
    filename: '[name]-bundle.js',
    clean: false,
    chunkLoading: false,
  },
  module: {
    rules: [
      {
        // docs: https://webpack.js.org/configuration/module/#resolvefullyspecified
        test: /\.m?js/,
        resolve: {
          fullySpecified: false,
        }
      }
    ]
  },
  plugins: [
    // fix "process is not defined" error:
    new webpack.ProvidePlugin({
      process: 'process/browser',
    }),
  ],
};
