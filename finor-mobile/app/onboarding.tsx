import { Redirect } from "expo-router";

// Keep existing links working after removing the initial setup flow.
export default function OnboardingRedirect() {
  return <Redirect href="/home" />;
}
