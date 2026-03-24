package com.sistemabancario02.banco02.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import com.sistemabancario02.banco02.entity.*;
import com.sistemabancario02.banco02.repository.*;
import java.util.List;
import lombok.*;

@Service
@RequiredArgsConstructor
public class ClienteService{

    private final ClienteRepository clienteRepository;

    public Cliente crearCliente(Cliente cliente) {


        cliente.setNombre(cliente.getNombre().toUpperCase()); // Esto está bien si quieres el nombre en mayúsculas
        cliente.setBloqueado(false); // Inicializa a false
        cliente.setIntentosFallidos(0); // Inicializa a 0
        return clienteRepository.save(cliente); // Guarda el cliente ya mapeado por Spring
    }

    public List<Cliente> obtenerTodosLosClientes() {
        return clienteRepository.findAll();
    }


    //Metodo con retorno de datos opcional que busca clientes por identificación
    public Optional<Cliente> buscarPorIdentificacionCliente(String identificacionCliente){
        return clienteRepository.findByIdentificacionCliente(identificacionCliente);
    }

    //Metodo para validar el pin del cliente con el cual puede acceder a los productos
    public boolean validarPin(Cliente cliente, String pin){
        //Devuelve false en caso de que el cliente al que se quiere acceder esta bloqueado
        if(cliente.isBloqueado()) return false;
        //Se compara el pin ingresado por el usuario con el pin real del usuario establecido en la DB
        //Si el pin es correcto no se agregan intentos fallidos y se guarda
        if(cliente.getPin().equals(pin)){
            cliente.setIntentosFallidos(0);
            //Se almacena la informacion almacenada hasta el momento a cerca de los intentos fallidos, ya sea que el pin fue valido o no 
            clienteRepository.save(cliente);
            return true;
        }else{
            //si se da el else a la condición anterior, se le sumara al valor actual del intentos fallidos un +1 y se volveran a almacenar en memoria.
            int intentos = cliente.getIntentosFallidos() + 1;
            cliente.setIntentosFallidos(intentos);
            /*
            * en esta condicion se evalua la cantidad de intentos fallidos, si es mayor a 3
            * se bloquea al cliente
            * */
            if(intentos >= 3){
                cliente.setBloqueado(true);
            }
            /*
            * Se Almacena los datos y valores cambiados
            * */
            clienteRepository.save(cliente);
            return false;

        }
        
    }
    //Metodo para desbloquear Un cliente bloqueado
    public void desbloquearCliente(String id, String nuevoPin){
        //Se establece optional porque puede o no que haya un usuario
        Optional<Cliente> optionalCliente = clienteRepository.findByIdentificacionCliente(id);
        //Se valida que el opcional ceunte como "PRESENTE"
        if(optionalCliente.isPresent()){
            //En caso de que este presente se obtienen los valores actuales de Cliente y se cambian los valores
            Cliente cliente = optionalCliente.get();
            //Se establece bloqueado como false
            cliente.setBloqueado(false);
            //Se establecen los intentos a 0 de nuevo
            cliente.setIntentosFallidos(0);
            //Se solicita un nuevo pin
            cliente.setPin(nuevoPin);
            clienteRepository.save(cliente);
        }


    }

    //Metodo para cambiar el pin y guardar el nuevo valor
    public void cambiarPin(Cliente cliente, String nuevoPin){
        if (nuevoPin == null || !nuevoPin.matches("\\d{4}")) {
            throw new RuntimeException("La nueva clave debe tener exactamente 4 digitos numericos.");
        }
        if (nuevoPin.equals(cliente.getPin())) {
            throw new RuntimeException("La nueva clave debe ser diferente a la actual.");
        }
        cliente.setPin(nuevoPin);
        clienteRepository.save(cliente);
    }

    //Metodo para incrementar intentos y guardar el valor
    public void incrementarIntento(Cliente cliente){
        cliente.setIntentosFallidos(cliente.getIntentosFallidos() + 1);
        clienteRepository.save(cliente);
    }

    //Metodo para Reiniciar la cantidad de intentos actuales y guardarlos
    public void reiniciarIntentos(Cliente cliente){
        cliente.setIntentosFallidos(0);
        clienteRepository.save(cliente);
    }

    //Metodo para Bloquear Cliente
    public void bloquearCliente(Cliente cliente){
        cliente.setBloqueado(true);
        clienteRepository.save(cliente);
    }


}
