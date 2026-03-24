package com.sistemabancario02.banco02.service;

import com.sistemabancario02.banco02.entity.Cuenta;
import com.sistemabancario02.banco02.entity.TipoMovimiento;
import com.sistemabancario02.banco02.repository.ClienteRepository;
import com.sistemabancario02.banco02.repository.CuentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RetiroService {

    private final CuentaRepository cuentaRepository;
    private final ClienteRepository clienteRepository;
    private final MovimientoService movimientoService;

    @Transactional
    public void realizarRetiro(String identificacionCliente, String numeroCuenta, double monto) {

        if (monto <= 0) {
            throw new RuntimeException("El monto del retiro debe ser mayor a cero.");
        }

        clienteRepository.findByIdentificacionCliente(identificacionCliente)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado."));

        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new RuntimeException("Cuenta no encontrada."));

        if (cuenta.getCliente() == null || !cuenta.getCliente().getIdentificacionCliente().equals(identificacionCliente)) {
            throw new RuntimeException("La cuenta no pertenece al cliente.");
        }

        if (cuenta.getCliente().isBloqueado()) {
            throw new RuntimeException("El cliente o su cuenta estan bloqueados.");
        }

        if (cuenta.getSaldo() < monto) {
            throw new RuntimeException("Saldo insuficiente en la cuenta " + numeroCuenta + ". Saldo actual: " + cuenta.getSaldo());
        }

        cuenta.setSaldo(cuenta.getSaldo() - monto);
        cuentaRepository.save(cuenta);
        movimientoService.registrarMovimiento(cuenta, monto, TipoMovimiento.RETIRO);
    }
}
