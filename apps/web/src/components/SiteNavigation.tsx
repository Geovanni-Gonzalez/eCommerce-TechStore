"use client";

import Link from "next/link";
import {usePathname} from "next/navigation";
import {useRef, useState, type KeyboardEvent} from "react";
import AccountNav from "./AccountNav";

const links = [
  {href:"/",label:"Inicio"},
  {href:"/productos",label:"Productos"},
  {href:"/carrito",label:"Carrito"},
  {href:"/pedidos",label:"Mis pedidos"},
] as const;

/** Navegación responsive accesible: sin dependencias ni menús solo al hover. */
export default function SiteNavigation() {
  const pathname = usePathname();
  const [open,setOpen] = useState(false);
  const toggleRef = useRef<HTMLButtonElement>(null);
  const close = () => setOpen(false);
  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Escape" && open) {
      setOpen(false);
      toggleRef.current?.focus();
    }
  };

  return (
    <div className="site-nav" onKeyDown={onKeyDown}>
      <button type="button" ref={toggleRef} className="site-nav__toggle"
        aria-controls="primary-navigation" aria-expanded={open}
        onClick={() => setOpen(value => !value)}>
        <span className="site-nav__toggle-lines" aria-hidden="true"><span/><span/><span/></span>
        <span>{open ? "Cerrar menú" : "Menú"}</span>
      </button>
      <nav id="primary-navigation" className={open ? "site-nav__panel site-nav__panel--open" : "site-nav__panel"}
        aria-label="Navegación principal">
        <ul className="nav-list">
          {links.map(({href,label}) => {
            const active = href === "/" ? pathname === "/" :
              pathname === href || pathname.startsWith(href + "/");
            return (
              <li key={href}>
                <Link href={href} onClick={close} aria-current={active ? "page" : undefined}>
                  {label}
                </Link>
              </li>
            );
          })}
          <AccountNav onNavigate={close} />
          <li className="site-nav__register">
            <Link href="/registro" onClick={close}>Crear cuenta</Link>
          </li>
        </ul>
      </nav>
    </div>
  );
}
