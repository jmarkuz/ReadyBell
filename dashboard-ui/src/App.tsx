import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { ThemeProvider } from '@/context/ThemeContext';
import { SearchProvider } from '@/context/SearchContext';
import Layout from '@/components/Layout';
import Dashboard from '@/pages/Dashboard';
import Orders from '@/pages/Orders';
import Menu from '@/pages/Menu';
import Payments from '@/pages/Payments';
import Analytics from '@/pages/Analytics';
import Settings from '@/pages/Settings';
import Support from '@/pages/Support';
import './App.css';

function App() {
  return (
    <ThemeProvider defaultMode="light" persistToLocalStorage>
      <SearchProvider>
        <Router>
          <Routes>
            <Route element={<Layout />}>
              <Route path="/" element={<Dashboard />} />
              <Route path="/orders" element={<Orders />} />
              <Route path="/menu" element={<Menu />} />
              <Route path="/payments" element={<Payments />} />
              <Route path="/analytics" element={<Analytics />} />
              <Route path="/settings" element={<Settings />} />
              <Route path="/support" element={<Support />} />
            </Route>
          </Routes>
        </Router>
      </SearchProvider>
    </ThemeProvider>
  );
}

export default App;
