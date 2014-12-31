var exec = require("cordova/exec"),
  PushConstants = require("./PushConstants");

"use strict";

var Push = function () {
  this._callback = function () {};
};

Push.prototype.register = function (successCallback, errorCallback, options) {
  options = options || {};
  var serverUrl = options.serverUrl || PushConstants.SERVER_URL + "/service/push/register",
    senderId = options.senderId || "",
    id = options.id || "";

  exec(successCallback, errorCallback, "Push", "register", [serverUrl, senderId, id]);
};

Push.prototype.unregister = function (successCallback, errorCallback, options) {
  options = options || {};
  var serverUrl = options.serverUrl || PushConstants.SERVER_URL + "/service/push/unregister",
    id = options.id || "";

  exec(successCallback, errorCallback, "Push", "unregister", [serverUrl, id]);
};

Push.prototype.addListener = function (successCallback, errorCallback) {
  this._callback = successCallback;
  exec(successCallback, errorCallback, "Push", "addListener", []);
};

module.exports = new Push();
