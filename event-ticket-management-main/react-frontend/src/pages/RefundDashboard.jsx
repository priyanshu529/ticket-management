import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { getRefundedBookings } from '../services/api';
import { useAuth } from '../context/AuthContext';
import {
  RotateCcw,
  User,
  Mail,
  Ticket,
  Calendar,
  MapPin,
  IndianRupee,
  Clock,
  AlertTriangle,
  ArrowLeft,
  RefreshCw,
  Download
} from 'lucide-react';

function fmt(isoStr) {
  if (!isoStr) return '—';
  const d = new Date(isoStr);
  return d.toLocaleString('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hour12: true
  });
}

function fmtAmount(n) {
  return '₹' + Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export default function RefundDashboard() {
  const [refunds, setRefunds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const { user } = useAuth();
  const navigate = useNavigate();

  const activeUser = user || JSON.parse(localStorage.getItem('user') || 'null');

  useEffect(() => {
    if (!activeUser) { navigate('/login'); return; }
    if (activeUser.role !== 'ADMIN') { navigate('/'); return; }
    fetchRefunds();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const fetchRefunds = async () => {
    try {
      setLoading(true);
      setError('');
      const res = await getRefundedBookings();
      setRefunds(res.data || []);
    } catch (err) {
      setError('Failed to load refund records. Make sure the booking-service is running.');
    } finally {
      setLoading(false);
    }
  };

  const filtered = refunds.filter(r => {
    const q = search.toLowerCase();
    return (
      (r.userName || '').toLowerCase().includes(q) ||
      (r.userEmail || '').toLowerCase().includes(q) ||
      (r.eventName || '').toLowerCase().includes(q) ||
      String(r.bookingId).includes(q)
    );
  });

  const totalRefundAmount = filtered.reduce((sum, r) => sum + Number(r.totalAmount || 0), 0);

  const exportCsv = () => {
    const header = ['Booking ID', 'User Name', 'User Email', 'Event', 'Venue', 'Event Date', 'Qty', 'Refund Amount', 'Confirmed At', 'Refunded At'];
    const rows = filtered.map(r => [
      r.bookingId, r.userName, r.userEmail, r.eventName,
      `${r.eventVenue || ''} ${r.eventCity || ''}`.trim(),
      fmt(r.eventDate), r.quantity, r.totalAmount, fmt(r.confirmedAt), fmt(r.refundedAt)
    ]);
    const csv = [header, ...rows].map(row => row.map(v => `"${String(v || '').replace(/"/g, '""')}"`).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a'); a.href = url; a.download = 'refunds.csv'; a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="w-full bg-[#070709] text-white min-h-screen pb-24">

      {/* Header */}
      <section className="py-14 px-4 sm:px-6 lg:px-8 border-b border-white/10 bg-gradient-to-b from-[#101119] to-[#070709]">
        <div className="max-w-7xl mx-auto space-y-4">
          <button
            onClick={() => navigate('/dashboard')}
            className="flex items-center gap-1.5 text-xs font-mono text-gray-400 hover:text-white transition-colors cursor-pointer"
          >
            <ArrowLeft size={13} /> Back to Dashboard
          </button>

          <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
            <div>
              <div className="flex items-center gap-2 mb-3">
                <span className="px-3 py-1 bg-red-500/20 border border-red-500/40 text-red-400 font-mono font-bold text-xs uppercase">
                  ADMIN
                </span>
                <span className="text-xs font-mono text-gray-400">REFUND MANAGEMENT</span>
              </div>
              <h1 className="font-syne font-black text-4xl sm:text-5xl uppercase tracking-tight">
                REFUND REQUESTS
              </h1>
              <p className="text-gray-400 font-mono text-xs max-w-xl mt-2">
                All users who requested a refund within the eligibility window. Send payment to the listed email addresses.
              </p>
            </div>

            {/* Summary stat */}
            <div className="flex gap-4">
              <div className="p-4 bg-white/5 border border-white/10 text-center min-w-[100px]">
                <div className="font-syne font-black text-2xl text-[#ccff00]">{filtered.length}</div>
                <div className="text-[10px] font-mono text-gray-400 uppercase mt-1">Refunds</div>
              </div>
              <div className="p-4 bg-white/5 border border-white/10 text-center min-w-[130px]">
                <div className="font-syne font-black text-xl text-red-400">{fmtAmount(totalRefundAmount)}</div>
                <div className="text-[10px] font-mono text-gray-400 uppercase mt-1">Total Pending</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Controls */}
      <section className="px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto py-6 flex flex-col sm:flex-row gap-3 items-start sm:items-center justify-between">
        <input
          type="text"
          placeholder="Search by name, email, event or booking ID..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="w-full sm:max-w-md px-4 py-2.5 bg-[#0e0f16] border border-white/15 focus:border-[#ccff00]/60 text-white font-mono text-xs outline-none placeholder:text-gray-600 transition-colors"
        />
        <div className="flex gap-2">
          <button
            onClick={fetchRefunds}
            className="px-4 py-2.5 bg-white/5 hover:bg-white/10 border border-white/15 hover:border-white/30 text-gray-300 font-mono text-xs flex items-center gap-1.5 cursor-pointer transition-all"
          >
            <RefreshCw size={12} /> Refresh
          </button>
          <button
            onClick={exportCsv}
            disabled={filtered.length === 0}
            className="px-4 py-2.5 bg-[#ccff00]/10 hover:bg-[#ccff00]/20 border border-[#ccff00]/40 text-[#ccff00] font-mono text-xs flex items-center gap-1.5 cursor-pointer transition-all disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <Download size={12} /> Export CSV
          </button>
        </div>
      </section>

      {/* Table */}
      <section className="px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto">

        {loading ? (
          <div className="py-24 text-center space-y-4">
            <div className="funky-spinner mx-auto" />
            <p className="font-mono text-xs text-gray-400 uppercase">Loading refund records...</p>
          </div>
        ) : error ? (
          <div className="p-8 bg-red-500/10 border border-red-500/30 flex items-center gap-3">
            <AlertTriangle size={18} className="text-red-400 shrink-0" />
            <p className="font-mono text-xs text-red-400">{error}</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="py-24 text-center space-y-4">
            <RotateCcw size={48} className="text-gray-700 mx-auto" />
            <p className="font-mono text-xs text-gray-500 uppercase">
              {search ? 'No refunds match your search.' : 'No refund requests yet.'}
            </p>
          </div>
        ) : (
          <>
            {/* Desktop table */}
            <div className="hidden md:block overflow-x-auto">
              <table className="w-full border-collapse font-mono text-xs">
                <thead>
                  <tr className="border-b border-white/10 text-gray-500 uppercase tracking-wider text-[10px]">
                    <th className="text-left py-3 px-4 font-semibold">#ID</th>
                    <th className="text-left py-3 px-4 font-semibold">
                      <span className="flex items-center gap-1"><User size={10} /> User</span>
                    </th>
                    <th className="text-left py-3 px-4 font-semibold">
                      <span className="flex items-center gap-1"><Mail size={10} /> Email</span>
                    </th>
                    <th className="text-left py-3 px-4 font-semibold">
                      <span className="flex items-center gap-1"><Ticket size={10} /> Event</span>
                    </th>
                    <th className="text-left py-3 px-4 font-semibold">
                      <span className="flex items-center gap-1"><Calendar size={10} /> Event Date</span>
                    </th>
                    <th className="text-center py-3 px-4 font-semibold">Qty</th>
                    <th className="text-right py-3 px-4 font-semibold">
                      <span className="flex items-center gap-1 justify-end"><IndianRupee size={10} /> Amount</span>
                    </th>
                    <th className="text-left py-3 px-4 font-semibold">
                      <span className="flex items-center gap-1"><Clock size={10} /> Refunded At</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((r, i) => (
                    <tr
                      key={r.bookingId}
                      className={`border-b border-white/5 hover:bg-white/5 transition-colors ${i % 2 === 0 ? 'bg-white/[0.02]' : ''}`}
                    >
                      <td className="py-3.5 px-4 text-gray-500">#{r.bookingId}</td>
                      <td className="py-3.5 px-4">
                        <span className="text-white font-bold">{r.userName || '—'}</span>
                      </td>
                      <td className="py-3.5 px-4">
                        <a href={`mailto:${r.userEmail}`} className="text-[#ccff00] hover:underline">
                          {r.userEmail || '—'}
                        </a>
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="text-white">{r.eventName}</div>
                        {(r.eventVenue || r.eventCity) && (
                          <div className="text-gray-500 text-[10px] flex items-center gap-1 mt-0.5">
                            <MapPin size={9} /> {r.eventVenue}{r.eventCity ? `, ${r.eventCity}` : ''}
                          </div>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-gray-300">{fmt(r.eventDate)}</td>
                      <td className="py-3.5 px-4 text-center text-gray-300">{r.quantity}</td>
                      <td className="py-3.5 px-4 text-right font-bold text-red-400">{fmtAmount(r.totalAmount)}</td>
                      <td className="py-3.5 px-4 text-gray-400">{fmt(r.refundedAt)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="border-t-2 border-white/20">
                    <td colSpan={6} className="py-3 px-4 text-right text-gray-400 uppercase text-[10px] tracking-wider">
                      Total refund amount ({filtered.length} records)
                    </td>
                    <td className="py-3 px-4 text-right font-black text-red-400 text-sm">
                      {fmtAmount(totalRefundAmount)}
                    </td>
                    <td />
                  </tr>
                </tfoot>
              </table>
            </div>

            {/* Mobile cards */}
            <div className="md:hidden space-y-4">
              {filtered.map(r => (
                <div key={r.bookingId} className="bg-[#0e0f16] border border-white/10 p-4 space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="font-mono text-[10px] text-gray-500">#{r.bookingId}</span>
                    <span className="font-mono text-sm font-black text-red-400">{fmtAmount(r.totalAmount)}</span>
                  </div>
                  <div className="space-y-1 font-mono text-xs">
                    <div className="flex items-center gap-2">
                      <User size={11} className="text-gray-500 shrink-0" />
                      <span className="text-white font-bold">{r.userName || '—'}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <Mail size={11} className="text-gray-500 shrink-0" />
                      <a href={`mailto:${r.userEmail}`} className="text-[#ccff00] hover:underline">{r.userEmail || '—'}</a>
                    </div>
                    <div className="flex items-center gap-2">
                      <Ticket size={11} className="text-gray-500 shrink-0" />
                      <span className="text-gray-300">{r.eventName} · {r.quantity} pass{r.quantity > 1 ? 'es' : ''}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <Clock size={11} className="text-gray-500 shrink-0" />
                      <span className="text-gray-400">Refunded: {fmt(r.refundedAt)}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}
      </section>
    </div>
  );
}