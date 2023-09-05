var helper = require("./helper");
var podFileModified = false;
var path = require("path");
var execSync = require('child_process').execSync;
module.exports = function(context) {
  var xcodeProjectPath = helper.getXcodeProjectPath(context);
  helper.ensureRunpathSearchPath(context, xcodeProjectPath);
  podFileModified = helper.applyPodsPostInstall();
  if(podFileModified){
    execSync('pod install', {
        cwd: path.resolve('platforms/ios'),
        encoding: 'utf8'
    });
  }
}
