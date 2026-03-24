package com.sistemabancario02.banco02.controller;

import java.util.Map;
import java.util.Optional;

import com.sistemabancario02.banco02.dto.TransferenciaForm;
import com.sistemabancario02.banco02.entity.Cliente;
import com.sistemabancario02.banco02.entity.Cuenta;
import com.sistemabancario02.banco02.service.ClienteService;
import com.sistemabancario02.banco02.service.CuentaService;
import com.sistemabancario02.banco02.service.MovimientoService;
import com.sistemabancario02.banco02.service.RetiroService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/cajero")
public class CajeroController {

    private final ClienteService clienteService;
    private final CuentaService cuentaService;
    private final MovimientoService movimientoService;
    private final RetiroService retiroService;

    private Cliente obtenerClienteAutenticado(HttpSession session) {
        return (Cliente) session.getAttribute("cliente");
    }

    private boolean cuentaPerteneceAlCliente(Cuenta cuenta, Cliente cliente) {
        if (cuenta == null || cuenta.getCliente() == null || cliente == null) {
            return false;
        }
        return cuenta.getCliente().getId() == cliente.getId();
    }

    @GetMapping("/login")
    public String mostrarLogin(Model model) {
        return "cajero/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String numeroCuenta,
                        @RequestParam String pin,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {

        Optional<Cuenta> cuentaOptional = cuentaService.buscarPorNumero(numeroCuenta);

        if (cuentaOptional.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Cuenta no encontrada.");
            return "redirect:/cajero/login";
        }

        Cuenta cuenta = cuentaOptional.get();
        Cliente cliente = cuenta.getCliente();

        if (cliente.isBloqueado()) {
            redirectAttributes.addFlashAttribute("error", "Cuenta bloqueada. Contacte a su banco.");
            return "redirect:/cajero/login";
        }

        if (!cliente.getPin().equals(pin)) {
            clienteService.incrementarIntento(cliente);

            if (cliente.getIntentosFallidos() >= 3) {
                clienteService.bloquearCliente(cliente);
                redirectAttributes.addFlashAttribute("error", "Cuenta bloqueada por multiples intentos fallidos.");
            } else {
                redirectAttributes.addFlashAttribute("error", "PIN incorrecto. Intentos restantes: " + (3 - cliente.getIntentosFallidos()));
            }
            return "redirect:/cajero/login";
        }

        clienteService.reiniciarIntentos(cliente);
        session.setAttribute("cliente", cliente);

        return "redirect:/cajero/menu";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/cajero/login";
    }

    @GetMapping("/menu")
    public String menu(HttpSession session, Model model) {
        Cliente cliente = obtenerClienteAutenticado(session);
        if (cliente == null) {
            return "redirect:/cajero/login";
        }

        model.addAttribute("cliente", cliente);
        model.addAttribute("cuentas", cuentaService.buscarPorCliente(cliente));
        return "cajero/menu";
    }

    @GetMapping("/consultas")
    public String consultas(Model model, HttpSession session) {
        Cliente cliente = obtenerClienteAutenticado(session);
        if (cliente == null) {
            return "redirect:/cajero/login";
        }
        model.addAttribute("cuentas", cuentaService.buscarPorCliente(cliente));
        return "cajero/consultas";
    }

    @GetMapping("/movimientos/{numero}")
    public String movimientos(@PathVariable String numero,
                              Model model,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        Cliente clienteSesion = obtenerClienteAutenticado(session);
        if (clienteSesion == null) {
            return "redirect:/cajero/login";
        }

        try {
            Optional<Cuenta> cuentaOptional = cuentaService.buscarPorNumero(numero);

            if (cuentaOptional.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Cuenta no encontrada.");
                return "redirect:/cajero/consultas";
            }

            Cuenta cuenta = cuentaOptional.get();
            if (!cuentaPerteneceAlCliente(cuenta, clienteSesion)) {
                redirectAttributes.addFlashAttribute("error", "Cuenta no encontrada o no pertenece a su usuario.");
                return "redirect:/cajero/consultas";
            }

            var movimientos = movimientoService.buscarPorCuenta(numero);
            model.addAttribute("movimientos", movimientos);
            model.addAttribute("numeroCuentaActual", numero);
            return "cajero/movimientos";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No fue posible obtener los movimientos: " + e.getMessage());
            return "redirect:/cajero/consultas";
        }
    }

    @GetMapping("/retiro")
    public String mostrarFormularioRetiro(Model model, HttpSession session) {
        Cliente cliente = obtenerClienteAutenticado(session);
        if (cliente == null) {
            return "redirect:/cajero/login";
        }

        model.addAttribute("cliente", cliente);
        model.addAttribute("cuentas", cuentaService.buscarPorCliente(cliente));
        return "cajero/retiro";
    }

    @PostMapping("/retiro")
    public String realizarRetiro(@RequestParam String identificacionCliente,
                                 @RequestParam String numeroCuenta,
                                 @RequestParam double monto,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        try {
            Cliente clienteSesion = obtenerClienteAutenticado(session);
            if (clienteSesion == null || !clienteSesion.getIdentificacionCliente().equals(identificacionCliente)) {
                redirectAttributes.addFlashAttribute("error", "Sesion invalida o datos inconsistentes.");
                return "redirect:/cajero/login";
            }

            Cuenta cuenta = cuentaService.buscarPorNumero(numeroCuenta)
                    .orElseThrow(() -> new RuntimeException("Cuenta no encontrada."));
            if (!cuentaPerteneceAlCliente(cuenta, clienteSesion)) {
                redirectAttributes.addFlashAttribute("error", "La cuenta seleccionada no pertenece a su usuario.");
                return "redirect:/cajero/retiro";
            }

            retiroService.realizarRetiro(identificacionCliente, numeroCuenta, monto);
            redirectAttributes.addFlashAttribute("mensaje", "Retiro exitoso.");
            return "redirect:/cajero/menu";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/cajero/retiro";
        }
    }

    @GetMapping("/consignar")
    public String mostrarFormularioConsignacion(HttpSession session, Model model) {
        Cliente cliente = obtenerClienteAutenticado(session);
        if (cliente == null) {
            return "redirect:/cajero/login";
        }
        model.addAttribute("cliente", cliente);
        model.addAttribute("cuentas", cuentaService.buscarPorCliente(cliente));
        return "cajero/consignar";
    }

    @PostMapping("/consignar")
    public String consignar(@RequestParam String numeroCuenta,
                            @RequestParam double monto,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        Cliente cliente = obtenerClienteAutenticado(session);
        if (cliente == null) {
            return "redirect:/cajero/login";
        }

        try {
            Cuenta cuenta = cuentaService.buscarPorNumero(numeroCuenta)
                    .orElseThrow(() -> new RuntimeException("Cuenta no encontrada."));
            if (!cuentaPerteneceAlCliente(cuenta, cliente)) {
                redirectAttributes.addFlashAttribute("error", "La cuenta seleccionada no pertenece a su usuario.");
                return "redirect:/cajero/consignar";
            }

            movimientoService.realizarConsignacion(cuenta, monto);
            redirectAttributes.addFlashAttribute("mensaje", "Consignacion exitosa. Nuevo saldo: " + cuenta.getSaldo());
            return "redirect:/cajero/menu";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error inesperado al consignar: " + e.getMessage());
        }
        return "redirect:/cajero/consignar";
    }

    @GetMapping("/transferir")
    public String mostrarFormularioTransferencia(Model model, HttpSession session) {
        Cliente cliente = obtenerClienteAutenticado(session);
        if (cliente == null) {
            return "redirect:/cajero/login";
        }
        model.addAttribute("transferenciaForm", new TransferenciaForm());
        model.addAttribute("cuentasOrigen", cuentaService.buscarPorCliente(cliente));
        return "cajero/transferir";
    }

    @PostMapping("/transferir")
    public String transferir(@RequestParam String numeroCuentaOrigen,
                             @RequestParam String numeroCuentaDestino,
                             @RequestParam double monto,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        Cliente cliente = obtenerClienteAutenticado(session);
        if (cliente == null) {
            return "redirect:/cajero/login";
        }

        try {
            Cuenta origen = cuentaService.buscarPorNumero(numeroCuentaOrigen)
                    .orElseThrow(() -> new RuntimeException("Cuenta origen no encontrada."));
            if (!cuentaPerteneceAlCliente(origen, cliente)) {
                throw new RuntimeException("La cuenta origen no pertenece a su usuario.");
            }

            Cuenta destino = cuentaService.buscarPorNumero(numeroCuentaDestino)
                    .orElseThrow(() -> new RuntimeException("Cuenta destino no encontrada."));

            if (movimientoService.realizarTransferencia(origen, monto, destino)) {
                redirectAttributes.addFlashAttribute("mensaje", "Transferencia realizada con exito.");
                return "redirect:/cajero/menu";
            } else {
                redirectAttributes.addFlashAttribute("error", "No se pudo realizar la transferencia (ej. saldo insuficiente).");
            }
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", "Error en la transferencia: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error inesperado al transferir: " + e.getMessage());
        }

        return "redirect:/cajero/transferir";
    }

    @GetMapping("/titular")
    @ResponseBody
    public Map<String, String> obtenerTitular(@RequestParam String numero, HttpSession session) {
        Cliente cliente = obtenerClienteAutenticado(session);
        if (cliente == null) {
            return Map.of();
        }

        return cuentaService.buscarPorNumero(numero)
                .filter(cuenta -> cuentaPerteneceAlCliente(cuenta, cliente))
                .map(cuenta -> Map.of("nombre", cuenta.getCliente().getNombre()))
                .orElse(Map.of());
    }

    @GetMapping("/cambiar-clave")
    public String mostrarFormularioCambioClave(HttpSession session, Model model) {
        Cliente cliente = obtenerClienteAutenticado(session);
        if (cliente == null) {
            return "redirect:/cajero/login";
        }
        model.addAttribute("cliente", cliente);
        return "cajero/cambiar-clave";
    }

    @PostMapping("/cambiar-clave")
    public String cambiarClave(@RequestParam String claveActual,
                               @RequestParam String nuevaClave,
                               @RequestParam String confirmarClave,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        Cliente cliente = obtenerClienteAutenticado(session);
        if (cliente == null) {
            return "redirect:/cajero/login";
        }

        if (!cliente.getPin().equals(claveActual)) {
            redirectAttributes.addFlashAttribute("error", "Clave actual incorrecta.");
            return "redirect:/cajero/cambiar-clave";
        }

        if (!nuevaClave.equals(confirmarClave)) {
            redirectAttributes.addFlashAttribute("error", "Las nuevas claves no coinciden.");
            return "redirect:/cajero/cambiar-clave";
        }

        try {
            clienteService.cambiarPin(cliente, nuevaClave);
            session.setAttribute("cliente", cliente);
            redirectAttributes.addFlashAttribute("mensaje", "Clave cambiada exitosamente.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", "Error al cambiar la clave: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error inesperado al cambiar la clave.");
        }

        return "redirect:/cajero/cambiar-clave";
    }
}
