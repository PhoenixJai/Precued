import { Navigate, Route, Routes } from "react-router-dom";
import CallPage from "./pages/CallPage";
import CustomTemplateBuilderPage from "./pages/CustomTemplateBuilderPage";
import GuestJoinPage from "./pages/GuestJoinPage";
import LandingPage from "./pages/LandingPage";
import LoginPage from "./pages/LoginPage";
import ProfilePage from "./pages/ProfilePage";
import RoomSetupPage from "./pages/RoomSetupPage";
import SignupPage from "./pages/SignupPage";
import TemplatePickerPage from "./pages/TemplatePickerPage";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/profile" element={<ProfilePage />} />
      <Route path="/join" element={<GuestJoinPage />} />
      <Route path="/join/:roomId/:roomRoleId" element={<GuestJoinPage />} />
      <Route path="/templates" element={<TemplatePickerPage />} />
      <Route path="/templates/custom/new" element={<CustomTemplateBuilderPage />} />
      <Route path="/templates/custom/:templateId" element={<CustomTemplateBuilderPage />} />
      <Route path="/rooms/:roomId/setup" element={<RoomSetupPage />} />
      <Route path="/rooms/:roomId/call" element={<CallPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
