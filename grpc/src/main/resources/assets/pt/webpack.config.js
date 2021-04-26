const path = require('path');

module.exports = {
    devtool: 'source-map',
    entry: './grpc/src/main/resources/assets/pt/src/index.js',
    output: {
        path: path.resolve(__dirname, 'dist'),
        filename: 'main.js',
    },
};
