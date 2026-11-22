"use client";

import Link from "next/link";
import { useRef, useState, type FormEvent } from "react";
import { useAuth } from "@/components/AuthProvider";
import {
  COSTA_RICA_PROVINCES, EMPTY_SHIPPING_ADDRESS, SHIPPING_FIELDS,
  makeShippingAddressBody, normalizeShippingAddress, validateShippingAddress,
  type ShippingAddress, type ShippingErrors, type ShippingField,
} from "@/lib/shipping-address";

const labels: Record<ShippingField, string> = {
  province: "Provincia",
  canton: "Cantón",
  district: "Distrito",
  details: "Señas y referencias",
  recipientName: "Nombre de quien recibe",
  phone: "Teléfono de contacto",
};

function DeliveryEditor({
  saved, save, clear,
}: {
  saved: ShippingAddress | null;
  save: (value: ShippingAddress) => void;
  clear: () => void;
}) {
  const [address, setAddress] = useState<ShippingAddress>(
    () => saved ?? { ...EMPTY_SHIPPING_ADDRESS },
  );
  const [errors, setErrors] = useState<ShippingErrors>({});
  const [review, setReview] = useState(Boolean(saved));
  const [message, setMessage] = useState("");
  const formRef = useRef<HTMLFormElement>(null);

  function edit(field: ShippingField, value: string) {
    setAddress(current => ({ ...current, [field]: value }));
    setErrors(current => ({ ...current, [field]: undefined }));
    setMessage("");
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = normalizeShippingAddress(address);
    const problems = validateShippingAddress(normalized);
    setErrors(problems);
    const first = SHIPPING_FIELDS.find(field => Boolean(problems[field]));
    if (first) {
      setMessage("Revisa los campos señalados antes de guardar la dirección.");
      const control = formRef.current?.elements.namedItem(first);
      if (control instanceof HTMLElement) control.focus();
      return;
    }
    // Solo preparamos el body permitido: no se crea el pedido en BKL-031.
    const payload = makeShippingAddressBody(normalized);
    save(payload.shippingAddress);
    setAddress(normalized);
    setReview(true);
    setMessage("Dirección revisada y preparada para el checkout.");
  }

  function discard() {
    clear();
    setAddress({ ...EMPTY_SHIPPING_ADDRESS });
    setErrors({});
    setMessage("");
    setReview(false);
  }

  return (
    <section className="delivery-page" aria-labelledby="delivery-heading">
      <header className="delivery-intro">
        <p className="eyebrow">Compra · Paso 1 de 3</p>
        <h1 id="delivery-heading">Dirección de entrega</h1>
        <p>Indica dónde recibirás tu pedido en Costa Rica.
          Podrás revisar los datos antes de confirmar una compra.</p>
      </header>
      {review ? (
        <div className="delivery-card" aria-labelledby="delivery-review-heading">
          <p className="eyebrow">Revisión de datos</p>
          <h2 id="delivery-review-heading">Dirección preparada</h2>
          <p role="status" className="delivery-success">
            Datos guardados temporalmente en esta sesión.
            Aún no se ha creado ningún pedido.
          </p>
          <dl className="delivery-review">
            {SHIPPING_FIELDS.map(field => (
              <div key={field}>
                <dt>{labels[field]}</dt>
                <dd>{address[field]}</dd>
              </div>
            ))}
          </dl>
          <div className="delivery-actions">
            <button type="button" className="delivery-secondary" onClick={() => {
              setReview(false);
              setMessage("");
            }}>Editar dirección</button>
            <button type="button" className="delivery-quiet" onClick={discard}>
              Borrar borrador
            </button>
          </div>
          <p className="delivery-note">
            La revisión del pedido, el total definitivo y su confirmación
            se incorporarán en BKL-034. Esta pantalla no realiza pagos ni pedidos.
          </p>
          <Link href="/carrito">Volver al carrito</Link>
        </div>
      ) : (
        <div className="delivery-card">
          <h2>Datos para el envío</h2>
          <p className="delivery-note">
            Todos los campos son obligatorios. Cantón y distrito se escriben
            manualmente: no se comprueba todavía su relación con la provincia.
          </p>
          {message && <p className="delivery-error" role="alert">{message}</p>}
          <form className="delivery-form" ref={formRef} onSubmit={submit} noValidate>
            <div className="delivery-fields">
              <div className="delivery-field">
                <label htmlFor="delivery-province">Provincia</label>
                <select id="delivery-province" name="province" required
                  value={address.province} aria-invalid={Boolean(errors.province)}
                  aria-describedby={errors.province ? "delivery-province-error" : undefined}
                  onChange={e => edit("province", e.target.value)}>
                  <option value="">Selecciona una provincia</option>
                  {COSTA_RICA_PROVINCES.map(province =>
                    <option key={province} value={province}>{province}</option>)}
                </select>
                {errors.province && <p className="delivery-field-error"
                  id="delivery-province-error">{errors.province}</p>}
              </div>
              <div className="delivery-field">
                <label htmlFor="delivery-canton">Cantón</label>
                <input id="delivery-canton" name="canton" type="text"
                  autoComplete="address-level2" maxLength={80} required
                  value={address.canton} aria-invalid={Boolean(errors.canton)}
                  aria-describedby={errors.canton ? "delivery-canton-error" : undefined}
                  onChange={e => edit("canton", e.target.value)} />
                {errors.canton && <p className="delivery-field-error"
                  id="delivery-canton-error">{errors.canton}</p>}
              </div>
              <div className="delivery-field">
                <label htmlFor="delivery-district">Distrito</label>
                <input id="delivery-district" name="district" type="text"
                  autoComplete="address-level3" maxLength={80} required
                  value={address.district} aria-invalid={Boolean(errors.district)}
                  aria-describedby={errors.district ? "delivery-district-error" : undefined}
                  onChange={e => edit("district", e.target.value)} />
                {errors.district && <p className="delivery-field-error"
                  id="delivery-district-error">{errors.district}</p>}
              </div>
              <div className="delivery-field">
                <label htmlFor="delivery-recipient">Nombre de quien recibe</label>
                <input id="delivery-recipient" name="recipientName" type="text"
                  autoComplete="name" maxLength={120} required
                  value={address.recipientName}
                  aria-invalid={Boolean(errors.recipientName)}
                  aria-describedby={errors.recipientName ? "delivery-recipient-error" : undefined}
                  onChange={e => edit("recipientName", e.target.value)} />
                {errors.recipientName && <p className="delivery-field-error"
                  id="delivery-recipient-error">{errors.recipientName}</p>}
              </div>
              <div className="delivery-field">
                <label htmlFor="delivery-phone">Teléfono de contacto</label>
                <input id="delivery-phone" name="phone" type="tel"
                  autoComplete="tel" maxLength={24} required
                  value={address.phone} aria-invalid={Boolean(errors.phone)}
                  aria-describedby={errors.phone
                    ? "delivery-phone-hint delivery-phone-error" : "delivery-phone-hint"}
                  onChange={e => edit("phone", e.target.value)} />
                <p id="delivery-phone-hint" className="delivery-hint">
                  De 8 a 24 caracteres. Ejemplo: 8888-1111.
                </p>
                {errors.phone && <p className="delivery-field-error"
                  id="delivery-phone-error">{errors.phone}</p>}
              </div>
              <div className="delivery-field delivery-field--wide">
                <label htmlFor="delivery-details">Señas y referencias</label>
                <textarea id="delivery-details" name="details" rows={4}
                  minLength={5} maxLength={300} required
                  value={address.details} aria-invalid={Boolean(errors.details)}
                  aria-describedby={errors.details
                    ? "delivery-details-hint delivery-details-error" : "delivery-details-hint"}
                  onChange={e => edit("details", e.target.value)} />
                <p id="delivery-details-hint" className="delivery-hint">
                  De 5 a 300 caracteres; incluye referencias para localizar el lugar.
                </p>
                {errors.details && <p className="delivery-field-error"
                  id="delivery-details-error">{errors.details}</p>}
              </div>
            </div>
            <div className="delivery-actions">
              <button type="submit" className="delivery-primary">
                Guardar y revisar dirección
              </button>
              <Link href="/carrito">Volver al carrito</Link>
            </div>
            <p className="delivery-note">
              Por privacidad, estos datos permanecen solo en memoria.
              Se borran al cerrar sesión o refrescar la página; nunca
              se guardan en URL, cookies ni almacenamiento del navegador.
            </p>
          </form>
        </div>
      )}
    </section>
  );
}

export default function DeliveryClient() {
  const { user, deliveryDraft, saveDeliveryDraft, clearDeliveryDraft } = useAuth();
  if (!user) {
    return (
      <section className="delivery-card" aria-labelledby="delivery-auth-title">
        <h1 id="delivery-auth-title">Inicia sesión para indicar tu dirección</h1>
        <p>Solo los clientes autenticados pueden preparar una dirección de entrega.</p>
        <Link className="button-link" href="/login">Iniciar sesión</Link>
      </section>
    );
  }
  if (user.role !== "CLIENT") {
    return (
      <section className="delivery-card" aria-labelledby="delivery-role-title">
        <h1 id="delivery-role-title">Acceso solo para clientes</h1>
        <p>Una cuenta administradora no puede preparar direcciones de compra.</p>
        <Link href="/productos">Volver a productos</Link>
      </section>
    );
  }
  return <DeliveryEditor key={user.id} saved={deliveryDraft}
    save={saveDeliveryDraft} clear={clearDeliveryDraft} />;
}
