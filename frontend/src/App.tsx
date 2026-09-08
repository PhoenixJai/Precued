import { Navigate, Route, Routes } from "react-router-dom";
import AuthPage from "./pages/AuthPage";
import CallPage from "./pages/CallPage";
import RoomSetupPage from "./pages/RoomSetupPage";
import TemplatePickerPage from "./pages/TemplatePickerPage";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<AuthPage />} />
      <Route path="/join" element={<AuthPage />} />
      <Route path="/join/:roomId/:roomRoleId" element={<AuthPage />} />
      <Route path="/templates" element={<TemplatePickerPage />} />
      <Route path="/rooms/:roomId/setup" element={<RoomSetupPage />} />
      <Route path="/rooms/:roomId/call" element={<CallPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
