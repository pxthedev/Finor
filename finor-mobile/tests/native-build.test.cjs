const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  validateBuildEnvironment,
  googleIosScheme,
} = require("../config/native-build.cjs");

test("standalone builds cannot point at a disconnected local server", () => {
  for (const profile of [
    "preview",
    "production",
    "ios-simulator",
    "ios-unsigned",
  ]) {
    for (const api of [
      undefined,
      "http://192.168.1.10:8080/api/v1",
      "https://localhost/api/v1",
      "https://127.0.0.1/api/v1",
    ]) {
      assert.throws(() =>
        validateBuildEnvironment({
          EAS_BUILD_PROFILE: profile,
          EXPO_PUBLIC_API_URL: api,
          EXPO_PUBLIC_FIREBASE_API_KEY: "test-firebase-key",
        }),
      );
    }
    assert.doesNotThrow(() =>
      validateBuildEnvironment({
        EAS_BUILD_PROFILE: profile,
        EXPO_PUBLIC_API_URL: "https://api.example.com/api/v1",
        EXPO_PUBLIC_FIREBASE_API_KEY: "test-firebase-key",
      }),
    );
  }
});

test("test credentials and HTTP exceptions do not go into installable builds", () => {
  const base = {
    EAS_BUILD_PROFILE: "production",
    EXPO_PUBLIC_API_URL: "https://api.example.com/api/v1",
    EXPO_PUBLIC_FIREBASE_API_KEY: "test-firebase-key",
  };
  for (const testConfig of [
    { EXPO_PUBLIC_FIREBASE_API_KEY: undefined },
    { EXPO_PUBLIC_FIREBASE_API_KEY: "demo-key" },
    { EXPO_PUBLIC_FIREBASE_AUTH_EMULATOR_URL: "http://127.0.0.1:9099" },
    { FINOR_ALLOW_LOCAL_NETWORK: "1" },
  ]) {
    assert.throws(() => validateBuildEnvironment({ ...base, ...testConfig }));
  }
  assert.doesNotThrow(() =>
    validateBuildEnvironment({
      EAS_BUILD_PROFILE: "development",
      EXPO_PUBLIC_API_URL: "http://127.0.0.1:8080/api/v1",
      FINOR_ALLOW_LOCAL_NETWORK: "1",
    }),
  );
});

test("Google iOS uses the reversed OAuth callback scheme and remains optional", () => {
  assert.equal(
    googleIosScheme("12345-ios.apps.googleusercontent.com"),
    "com.googleusercontent.apps.12345-ios",
  );
  assert.equal(googleIosScheme(undefined), undefined);
  assert.throws(() => googleIosScheme("12345-ios"));
});
