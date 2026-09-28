import React, { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getUserBookings, requestRefund } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { 
  Ticket, 
  Calendar, 
  MapPin, 
  QrCode, 
  ArrowRight,
  Download,
  CalendarPlus,
  CheckCircle2,
  ShieldCheck,
  RotateCcw,
  Clock,
  XCircle,
  AlertTriangle,
  X
} from 'lucide-react';
import AlienLogo from '../components/AlienLogo';

/**
 * Returns the refund eligibility state for a booking.
 */
function getRefundEligibility(booking) {
  if (booking.status !== 'CONFIRMED') {
    return { eligible: false, reason: null, minutesLeft: null };
  }

  const now = new Date();

  // Rule 1: Refund option closes 24 hours before the event
  if (booking.eventDate) {
    const eventDate = new Date(booking.eventDate);
    const cutoff = new Date(eventDate.getTime() - 24 * 60 * 60 * 1000);
    if (now >= cutoff) {
      return {
        eligible: false,
        reason: 'Refund closed — event is less than 24 hours away',
        minutesLeft: null,
      };
    }
  }

  // Rule 2: Refund only available within 6 hours of confirmation
  if (!booking.confirmedAt) {
    return {
      eligible: false,
      reason: 'Refund window information unavailable for this booking',
      minutesLeft: null,
    };
  }

  const confirmedAt = new Date(booking.confirmedAt);
  const windowEnd = new Date(confirmedAt.getTime() + 6 * 60 * 60 * 1000);
  const minutesLeft = Math.floor((windowEnd - now) / 60000);

  if (now >= windowEnd) {
    return {
      eligible: false,
      reason: 'Refund window closed — refunds are only available within 6 hours of confirmation',
      minutesLeft: 0,
    };
  }

  return { eligible: true, reason: null, minutesLeft };
}

export default function BookingList() {
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refundingId, setRefundingId] = useState(null);
  const [confirmingId, setConfirmingId] = useState(null); // which booking is showing the in-page confirm panel
  const [toastMessage, setToastMessage] = useState('');
  const [toastType, setToastType] = useState('success');
  const [refundedBooking, setRefundedBooking] = useState(null); // show inline success banner for this booking
  const { user } = useAuth();
  const navigate = useNavigate();

  const fetchBookings = useCallback(async () => {
    const activeUser = user || JSON.parse(localStorage.getItem('user') || 'null');
    if (!activeUser || !activeUser.id) return;
    try {
      setLoading(true);
      const response = await getUserBookings(activeUser.id);
      const fetched = response.data || [];
      // Remove bookings whose event has already started/passed
      const now = new Date();
      const active = fetched.filter(b => {
        if (!b.eventDate) return true; // no date = keep (safe default)
        return new Date(b.eventDate) > now;
      });
      active.sort((a, b) => (b.id || 0) - (a.id || 0));
      setBookings(active);
    } catch (error) {
      console.error('Failed to fetch user bookings', error);
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    const activeUser = user || JSON.parse(localStorage.getItem('user') || 'null');
    if (!activeUser) {
      navigate('/login');
      return;
    }
    fetchBookings();
  }, [user, navigate, fetchBookings]);

  const showToast = (msg, type = 'success') => {
    setToastMessage(msg);
    setToastType(type);
    setTimeout(() => setToastMessage(''), 4000);
  };

  const handleRefundClick = (booking) => {
    setConfirmingId(booking.id);
  };

  const handleRefundCancel = () => {
    setConfirmingId(null);
  };

  const handleRefundConfirm = async (booking) => {
    try {
      setRefundingId(booking.id);
      setConfirmingId(null);
      await requestRefund(booking.id);
      setRefundedBooking(booking);
      await fetchBookings();
    } catch (error) {
      const msg = error.response?.data?.message || error.response?.data?.error || 'Refund request failed.';
      const cleanMsg = msg.replace(/^[A-Z_]+:\s*/, '');
      showToast(cleanMsg, 'error');
    } finally {
      setRefundingId(null);
    }
  };

  const handlePrintPass = () => {
    window.print();
  };

  const handleAddToCalendar = (booking) => {
    const eventName = encodeURIComponent(booking.eventName || 'Live Event Experience');
    const venue = encodeURIComponent(`${booking.eventVenue || 'Venue'}, ${booking.eventCity || ''}`);
    const details = encodeURIComponent(`Eventified Digital Ticket #${booking.id} - ${booking.quantity} Pass(es). Verified Entry.`);
    const googleCalendarUrl = `https://calendar.google.com/calendar/render?action=TEMPLATE&text=${eventName}&details=${details}&location=${venue}`;
    window.open(googleCalendarUrl, '_blank');
  };

  return (
    <div className="w-full bg-[#070709] text-white min-h-screen pb-24">
      
      {/* Toast Notification */}
      {toastMessage && (
        <div className={`fixed top-24 right-6 z-50 p-4 font-mono text-xs shadow-2xl flex items-center gap-2 max-w-sm ${
          toastType === 'error'
            ? 'bg-[#0f1017] border border-red-500 text-red-400'
            : 'bg-[#0f1017] border border-[#ccff00] text-[#ccff00] animate-bounce'
        }`}>
          {toastType === 'error' ? <XCircle size={16} /> : <CheckCircle2 size={16} />}
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Header */}
      <section className="py-14 px-4 sm:px-6 lg:px-8 border-b border-white/10 bg-gradient-to-b from-[#101119] to-[#070709]">
        <div className="max-w-7xl mx-auto space-y-4">
          <div className="flex items-center gap-2">
            <AlienLogo className="w-5 h-5" glow={false} />
            <span className="px-3 py-1 bg-[#ccff00] text-black font-mono font-bold text-xs uppercase">
              PASS VAULT
            </span>
            <span className="text-xs font-mono text-gray-400">EVENTIFIED DIGITAL PASSES</span>
          </div>
          <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
            <div>
              <h1 className="font-syne font-black text-4xl sm:text-6xl uppercase tracking-tight">
                MY DIGITAL PASSES
              </h1>
              <p className="text-gray-400 font-mono text-xs max-w-xl mt-2">
                Present your pass at the door. Dynamic cryptographic authentication ensures zero duplicate entries.
              </p>
            </div>
            <div className="p-3 bg-white/5 border border-white/15 flex items-center gap-2 text-xs font-mono text-gray-300">
              <ShieldCheck size={16} className="text-[#ccff00]" />
              <span>SECURE DIGITAL PASS VAULT</span>
            </div>
          </div>
        </div>
      </section>

      {/* Main Content */}
      <section className="py-12 px-4 sm:px-6 lg:px-8 max-w-5xl mx-auto">
        
        {loading ? (
          <div className="py-24 text-center space-y-4">
            <div className="funky-spinner mx-auto" />
            <p className="font-mono text-xs text-gray-400 uppercase">Decrypting your ticket pass vault...</p>
          </div>
        ) : bookings.length === 0 ? (
          <div className="p-16 text-center bg-[#0e0f16] border border-white/15 space-y-6">
            <Ticket size={56} className="text-gray-600 mx-auto" />
            <h3 className="font-syne font-black text-2xl sm:text-3xl text-white uppercase">
              NO ACTIVE PASSES IN YOUR VAULT
            </h3>
            <p className="text-gray-400 text-xs font-mono max-w-md mx-auto">
              You have not reserved tickets for upcoming shows yet. Discover our curated calendar and secure your access.
            </p>
            <Link to="/events" className="btn-funky-primary text-xs inline-flex">
              <span>EXPLORE LIVE SHOWS</span>
              <ArrowRight size={14} />
            </Link>
          </div>
        ) : (
          <div className="space-y-8">
            {bookings.map((booking) => {
              const isConfirmed = booking.status === 'CONFIRMED';
              const { eligible: refundEligible, reason: refundReason, minutesLeft } = getRefundEligibility(booking);
              const isConfirming = confirmingId === booking.id;
              const isRefunding = refundingId === booking.id;
              const wasJustRefunded = refundedBooking?.id === booking.id;

              return (
                <div key={booking.id} className="space-y-0">
                  <div 
                    className={`ticket-stub bg-[#11121b] border border-white/20 p-6 sm:p-8 flex flex-col md:flex-row items-stretch justify-between gap-6 relative overflow-hidden transition-all ${
                      isConfirming ? 'border-b-0 border-red-500/40' : ''
                    }`}
                  >
                    {/* Left Ticket Details */}
                    <div className="flex-1 space-y-5">
                      <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-white/10">
                        <div className="flex items-center gap-2">
                          <AlienLogo className="w-5 h-5" glow={false} />
                          <span className="badge-lime">EVENTIFIED PASS</span>
                          <span className="text-xs font-mono text-gray-400">#PASS-{booking.id}</span>
                        </div>
                        <span 
                          className={`text-xs font-mono font-bold px-2.5 py-1 uppercase ${
                            booking.status === 'CONFIRMED' 
                              ? 'bg-[#ccff00]/15 text-[#ccff00] border border-[#ccff00]/40' 
                              : booking.status === 'PENDING_PAYMENT'
                              ? 'bg-yellow-500/15 text-yellow-400 border border-yellow-500/40'
                              : booking.status === 'EXPIRED'
                              ? 'bg-gray-500/15 text-gray-400 border border-gray-500/40'
                              : 'bg-red-500/15 text-red-400 border border-red-500/40'
                          }`}
                        >
                          {booking.status}
                        </span>
                      </div>

                      <div>
                        <h3 className="font-syne font-black text-2xl sm:text-3xl text-white uppercase leading-tight">
                          {booking.eventName || 'Live Event Experience'}
                        </h3>
                        <p className="text-gray-400 text-xs font-mono mt-1">
                          Attendee: <span className="text-white font-bold">{user?.name || user?.email}</span>
                        </p>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-2 font-mono text-xs text-gray-300">
                        <div className="flex items-center gap-2">
                          <Calendar size={14} className="text-[#ccff00]" />
                          <span>{booking.eventDate || 'TBA'}</span>
                        </div>
                        <div className="flex items-center gap-2">
                          <MapPin size={14} className="text-[#ccff00]" />
                          <span>{booking.eventVenue || 'Venue'}, {booking.eventCity}</span>
                        </div>
                        <div className="flex items-center gap-2">
                          <Ticket size={14} className="text-[#ccff00]" />
                          <span>{booking.quantity} Pass{booking.quantity > 1 ? 'es' : ''}</span>
                        </div>
                      </div>

                      {/* Action Bar */}
                      {isConfirmed && (
                        <div className="flex flex-wrap items-center gap-3 pt-3 border-t border-white/10 text-xs font-mono">
                          <button
                            onClick={() => handlePrintPass(booking)}
                            className="px-3 py-1.5 bg-white/5 hover:bg-white/10 border border-white/15 hover:border-[#ccff00] text-gray-300 hover:text-white transition-all flex items-center gap-1.5 cursor-pointer"
                          >
                            <Download size={13} className="text-[#ccff00]" />
                            <span>PRINT / SAVE PDF</span>
                          </button>
                          <button
                            onClick={() => handleAddToCalendar(booking)}
                            className="px-3 py-1.5 bg-white/5 hover:bg-white/10 border border-white/15 hover:border-[#ccff00] text-gray-300 hover:text-white transition-all flex items-center gap-1.5 cursor-pointer"
                          >
                            <CalendarPlus size={13} className="text-[#00f0ff]" />
                            <span>GOOGLE CALENDAR</span>
                          </button>
                        </div>
                      )}
                    </div>

                    {/* Right Stub */}
                    <div className="md:w-64 pt-6 md:pt-0 md:pl-8 border-t md:border-t-0 md:border-l border-dashed border-white/20 flex flex-col justify-between items-center text-center space-y-4">
                      
                      <div className="space-y-1">
                        <span className="text-[10px] font-mono text-gray-400 uppercase">TOTAL SETTLED</span>
                        <div className="font-syne font-black text-2xl text-[#ccff00]">
                          ₹{Number(booking.totalAmount || 0).toFixed(2)}
                        </div>
                      </div>

                      <div className="p-3 bg-black/80 border border-white/15 space-y-1.5 w-full flex flex-col items-center">
                        <div className="w-20 h-20 bg-white p-1.5 flex items-center justify-center">
                          <QrCode size={64} className="text-black" />
                        </div>
                        <span className="font-mono text-[9px] text-gray-400 tracking-widest">
                          {isConfirmed ? 'VALID ENTRY QR' : 'VOID PASS'}
                        </span>
                      </div>

                      {/* Refund button area — only for CONFIRMED bookings */}
                      {isConfirmed && (
                        <div className="w-full space-y-1.5">
                          {refundEligible ? (
                            <>
                              <button
                                onClick={() => handleRefundClick(booking)}
                                disabled={isRefunding || isConfirming}
                                className={`w-full text-[11px] font-mono px-3 py-2 border flex items-center justify-center gap-1.5 transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed ${
                                  isConfirming
                                    ? 'bg-red-500/25 border-red-400 text-red-300'
                                    : 'bg-red-500/10 hover:bg-red-500/20 border-red-500/40 hover:border-red-400 text-red-400 hover:text-red-300'
                                }`}
                              >
                                <RotateCcw size={12} />
                                {isRefunding ? 'Processing...' : isConfirming ? 'Confirm below ↓' : 'Request Refund'}
                              </button>
                              {minutesLeft !== null && !isConfirming && (
                                <div className="flex items-center justify-center gap-1 text-[9px] font-mono text-yellow-500/80">
                                  <Clock size={9} />
                                  <span>Window closes in {minutesLeft}m</span>
                                </div>
                              )}
                            </>
                          ) : (
                            <div className="w-full text-[10px] font-mono px-3 py-2 bg-white/5 border border-white/10 text-gray-500 flex items-start gap-1.5">
                              <AlertTriangle size={11} className="mt-0.5 shrink-0 text-gray-600" />
                              <span className="text-left leading-tight">{refundReason}</span>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  </div>

                  {/* ── In-page Refund Confirmation Panel ── */}
                  {isConfirming && (
                    <div className="border border-t-0 border-red-500/40 bg-[#160d0d] p-5 space-y-4">
                      {/* Header row */}
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <RotateCcw size={14} className="text-red-400" />
                          <span className="font-mono text-xs font-bold text-red-400 uppercase tracking-wider">
                            Confirm Refund Request
                          </span>
                        </div>
                        <button
                          onClick={handleRefundCancel}
                          className="text-gray-500 hover:text-white transition-colors cursor-pointer"
                          title="Cancel"
                        >
                          <X size={15} />
                        </button>
                      </div>

                      {/* Detail bullets */}
                      <div className="space-y-2 font-mono text-xs text-gray-300 pl-1">
                        <div className="flex items-start gap-2">
                          <XCircle size={12} className="mt-0.5 shrink-0 text-red-400" />
                          <span>
                            Your booking for <span className="text-white font-bold">{booking.eventName}</span> will be <span className="text-red-400 font-bold">cancelled immediately</span>.
                          </span>
                        </div>
                        <div className="flex items-start gap-2">
                          <CheckCircle2 size={12} className="mt-0.5 shrink-0 text-[#ccff00]" />
                          <span>
                            <span className="text-white font-bold">{booking.quantity} seat{booking.quantity > 1 ? 's' : ''}</span> will be released back to the public pool.
                          </span>
                        </div>
                        <div className="flex items-start gap-2">
                          <Clock size={12} className="mt-0.5 shrink-0 text-yellow-400" />
                          <span>
                            <span className="text-[#ccff00] font-bold">₹{Number(booking.totalAmount || 0).toFixed(2)}</span> will be refunded to your original payment method within <span className="text-white font-bold">3–5 working days</span>.
                          </span>
                        </div>
                        {minutesLeft !== null && (
                          <div className="flex items-start gap-2">
                            <AlertTriangle size={12} className="mt-0.5 shrink-0 text-yellow-500" />
                            <span className="text-yellow-500/80">
                              You have <span className="font-bold text-yellow-400">{minutesLeft} minute{minutesLeft !== 1 ? 's' : ''}</span> left in your refund window.
                            </span>
                          </div>
                        )}
                      </div>

                      {/* Action buttons */}
                      <div className="flex items-center gap-3 pt-1">
                        <button
                          onClick={() => handleRefundConfirm(booking)}
                          className="flex-1 py-2 bg-red-500/20 hover:bg-red-500/30 border border-red-500/60 hover:border-red-400 text-red-300 hover:text-white font-mono text-xs font-bold uppercase tracking-wide transition-all flex items-center justify-center gap-1.5 cursor-pointer"
                        >
                          <RotateCcw size={12} />
                          Yes, Refund My Booking
                        </button>
                        <button
                          onClick={handleRefundCancel}
                          className="flex-1 py-2 bg-white/5 hover:bg-white/10 border border-white/15 hover:border-white/30 text-gray-400 hover:text-white font-mono text-xs uppercase tracking-wide transition-all cursor-pointer"
                        >
                          Keep My Pass
                        </button>
                      </div>
                    </div>
                  )}

                  {/* ── In-page Refund Success Banner ── */}
                  {wasJustRefunded && booking.status === 'CANCELLED' && (
                    <div className="border border-t-0 border-[#ccff00]/30 bg-[#0a0f08] p-5 space-y-3">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <CheckCircle2 size={14} className="text-[#ccff00]" />
                          <span className="font-mono text-xs font-bold text-[#ccff00] uppercase tracking-wider">
                            Refund Initiated
                          </span>
                        </div>
                        <button
                          onClick={() => setRefundedBooking(null)}
                          className="text-gray-500 hover:text-white transition-colors cursor-pointer"
                        >
                          <X size={15} />
                        </button>
                      </div>
                      <div className="space-y-1.5 font-mono text-xs text-gray-300 pl-1">
                        <div className="flex items-center gap-2">
                          <CheckCircle2 size={11} className="text-[#ccff00] shrink-0" />
                          <span>Booking cancelled &amp; seats restored to the public pool.</span>
                        </div>
                        <div className="flex items-center gap-2">
                          <Clock size={11} className="text-yellow-400 shrink-0" />
                          <span>
                            <span className="text-[#ccff00] font-bold">₹{Number(booking.totalAmount || 0).toFixed(2)}</span> will be credited to your original payment method within{' '}
                            <span className="text-white font-bold">3–5 working days</span>.
                          </span>
                        </div>
                      </div>
                    </div>
                  )}

                </div>
              );
            })}
          </div>
        )}

      </section>

    </div>
  );
}