var fs = require("fs");
var path = require("path");
var utilities = require("../../utilities");

/**
 * This is used as the display text for the build phase block in XCode as well as the
 * inline comments inside of the .pbxproj file for the build script phase block.
 */
var comment = "\"Crashlytics\"";
var iosDeploymentTargetPodRegEx = /platform :ios, '(\d+\.\d+)'/;
module.exports = {

  /**
     * Used to get the path to the XCode project's .pbxproj file.
     *
     * @param {object} context - The Cordova context.
     * @returns The path to the XCode project's .pbxproj file.
     */
  getXcodeProjectPath: function (context) {

    var appName = utilities.getAppName(context);

    return path.join("platforms", "ios", appName + ".xcodeproj", "project.pbxproj");
  },

  /**
     * This helper is used to add a build phase to the XCode project which runs a shell
     * script during the build process. The script executes Crashlytics run command line
     * tool with the API and Secret keys. This tool is used to upload the debug symbols
     * (dSYMs) so that Crashlytics can display stack trace information in it's web console.
     */
  addShellScriptBuildPhase: function (context, xcodeProjectPath) {

    let xcode;
    if (cmpVersions(context.opts.cordova.version, '8.0.0') < 0) {
      xcode = context.requireCordovaModule("xcode");
    //var xcode = context.requireCordovaModule("xcode");
    } else {
      xcode = require('xcode');
    }

    // Read and parse the XCode project (.pxbproj) from disk.
    // File format information: http://www.monobjc.net/xcode-project-file-format.html
    var xcodeProject = xcode.project(xcodeProjectPath);
    xcodeProject.parseSync();

    // Build the body of the script to be executed during the build phase.
    // var script = '"' + '\\"${SRCROOT}\\"' + "/\\\"" + utilities.getAppName(context) + "\\\"/Plugins/" + utilities.getPluginId() + "/Fabric.framework/run" + '"';
    var script = '"' + '\\"${PODS_ROOT}/FirebaseCrashlytics/run\\"' + '"';

    // Generate a unique ID for our new build phase.
    var id = xcodeProject.generateUuid();
    // Create the build phase.
    xcodeProject.hash.project.objects.PBXShellScriptBuildPhase[id] = {
          isa: "PBXShellScriptBuildPhase",
          buildActionMask: 2147483647,
          files: [],
          inputPaths: ['"' + '$(BUILT_PRODUCTS_DIR)/$(INFOPLIST_PATH)' + '"'],
          name: comment,
          outputPaths: [],
          runOnlyForDeploymentPostprocessing: 0,
          shellPath: "/bin/sh",
          shellScript: script,
          showEnvVarsInLog: 0
        };

    // Add a comment to the block (viewable in the source of the pbxproj file).
    xcodeProject.hash.project.objects.PBXShellScriptBuildPhase[id + "_comment"] = comment;

    // Add this new shell script build phase block to the targets.
    for (var nativeTargetId in xcodeProject.hash.project.objects.PBXNativeTarget) {

      // Skip over the comment blocks.
      if (nativeTargetId.indexOf("_comment") !== -1) {
        continue;
      }

      var nativeTarget = xcodeProject.hash.project.objects.PBXNativeTarget[nativeTargetId];

      nativeTarget.buildPhases.push({
        value: id,
        comment: comment
      });
    }

    // Finally, write the .pbxproj back out to disk.
    fs.writeFileSync(xcodeProjectPath, xcodeProject.writeSync());
  },

  /**
     * This helper is used to remove the build phase from the XCode project that was added
     * by the addShellScriptBuildPhase() helper method.
     */
  removeShellScriptBuildPhase: function (context, xcodeProjectPath) {


    let xcode;
    if (cmpVersions(context.opts.cordova.version, '8.0.0') < 0) {
      xcode = context.requireCordovaModule("xcode");
    //var xcode = context.requireCordovaModule("xcode");
    } else {
      xcode = require('xcode');
    }

    // Read and parse the XCode project (.pxbproj) from disk.
    // File format information: http://www.monobjc.net/xcode-project-file-format.html
    var xcodeProject = xcode.project(xcodeProjectPath);
    xcodeProject.parseSync();

    // First, we want to delete the build phase block itself.

    var buildPhases = xcodeProject.hash.project.objects.PBXShellScriptBuildPhase;

    for (var buildPhaseId in buildPhases) {

      var buildPhase = xcodeProject.hash.project.objects.PBXShellScriptBuildPhase[buildPhaseId];
      var shouldDelete = false;

      if (buildPhaseId.indexOf("_comment") === -1) {
        // Dealing with a build phase block.

        // If the name of this block matches ours, then we want to delete it.
        shouldDelete = buildPhase.name && buildPhase.name.indexOf(comment) !== -1;
      } else {
        // Dealing with a comment block.

        // If this is a comment block that matches ours, then we want to delete it.
        shouldDelete = buildPhaseId === comment;
      }

      if (shouldDelete) {
        delete buildPhases[buildPhaseId];
      }
    }

    // Second, we want to delete the native target reference to the block.

    var nativeTargets = xcodeProject.hash.project.objects.PBXNativeTarget;

    for (var nativeTargetId in nativeTargets) {

      // Skip over the comment blocks.
      if (nativeTargetId.indexOf("_comment") !== -1) {
        continue;
      }

      var nativeTarget = nativeTargets[nativeTargetId];

      // We remove the reference to the block by filtering out the the ones that match.
      nativeTarget.buildPhases = nativeTarget.buildPhases.filter(function (buildPhase) {
        return buildPhase.comment !== comment;
      });
    }

    // Finally, write the .pbxproj back out to disk.
    fs.writeFileSync(xcodeProjectPath, xcodeProject.writeSync());
  },
	applyPodsPostInstall: function(){
    var podFileModified = false,
        podFilePath = "platforms/ios/Podfile",
        podFile = fs.readFileSync(path.resolve(podFilePath)).toString(),
        IPHONEOS_DEPLOYMENT_TARGET = podFile.match(iosDeploymentTargetPodRegEx)[1];

    if(!podFile.match('post_install')){
        podFile += `
post_install do |installer|
installer.pods_project.targets.each do |target|
    target.build_configurations.each do |config|
        config.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = '${IPHONEOS_DEPLOYMENT_TARGET}'
    end
end
end
            `;
        fs.writeFileSync(path.resolve(podFilePath), podFile);
        console.log('cordova-plugin-firebase: Applied post install block to Podfile');
        podFileModified = true;
    }
    return podFileModified;
  },
  ensureRunpathSearchPath: function (context, xcodeProjectPath) {
    let xcode;
    if (cmpVersions(context.opts.cordova.version, '8.0.0') < 0) {
      xcode = context.requireCordovaModule("xcode");
    } else {
      xcode = require('xcode');
    }

    function addRunpathSearchBuildProperty(proj, build) {
        let LD_RUNPATH_SEARCH_PATHS = proj.getBuildProperty("LD_RUNPATH_SEARCH_PATHS", build);

        if (!Array.isArray(LD_RUNPATH_SEARCH_PATHS)) {
            LD_RUNPATH_SEARCH_PATHS = [LD_RUNPATH_SEARCH_PATHS];
        }

        LD_RUNPATH_SEARCH_PATHS.forEach(LD_RUNPATH_SEARCH_PATH => {
            if (!LD_RUNPATH_SEARCH_PATH) {
                proj.addBuildProperty("LD_RUNPATH_SEARCH_PATHS", "\"$(inherited) @executable_path/Frameworks\"", build);
            }
            if (LD_RUNPATH_SEARCH_PATH.indexOf("@executable_path/Frameworks") == -1) {
                var newValue = LD_RUNPATH_SEARCH_PATH.substr(0, LD_RUNPATH_SEARCH_PATH.length - 1);
                newValue += ' @executable_path/Frameworks\"';
              	console.log ("Search Path executable path::"+newValue);
                proj.updateBuildProperty("LD_RUNPATH_SEARCH_PATHS", newValue, build);
            }
            if (LD_RUNPATH_SEARCH_PATH.indexOf("$(inherited)") == -1) {
                var newValue = LD_RUNPATH_SEARCH_PATH.substr(0, LD_RUNPATH_SEARCH_PATH.length - 1);
                newValue = '"$(inherited)"';
              	console.log ("Search Path inherited::"+newValue);
                proj.updateBuildProperty("LD_RUNPATH_SEARCH_PATHS", newValue, build);
            }
        });
    }

    // Read and parse the XCode project (.pxbproj) from disk.
    // File format information: http://www.monobjc.net/xcode-project-file-format.html
    var xcodeProject = xcode.project(xcodeProjectPath);
    console.log ("xCodeProject Path Firebase::"+JSON.stringify(xcodeProject));
    xcodeProject.parseSync();

    // Add search paths build property
    addRunpathSearchBuildProperty(xcodeProject, "Debug");
    addRunpathSearchBuildProperty(xcodeProject, "Release");

    // Finally, write the .pbxproj back out to disk.
    fs.writeFileSync(path.resolve(xcodeProjectPath), xcodeProject.writeSync());
  }
};

function cmpVersions (a, b) {
  var i, diff;
  var regExStrip0 = /(\.0+)+$/;
  var segmentsA = a.replace(regExStrip0, '').split('.');
  var segmentsB = b.replace(regExStrip0, '').split('.');
  var l = Math.min(segmentsA.length, segmentsB.length);

  for (i = 0; i < l; i++) {
      diff = parseInt(segmentsA[i], 10) - parseInt(segmentsB[i], 10);
      if (diff) {
          return diff;
      }
  }
  return segmentsA.length - segmentsB.length;
}