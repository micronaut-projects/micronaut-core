import React from 'react';
import config from "../config";

const PetsCell = ({pet}) =>
    <div className="col-sm" style={{
      backgroundImage: `url(${config.SERVER_URL}/images/${pet.image})`,
      backgroundPosition: 'center',
      backgroundRepeat: 'no-repeat',
      'background-size': 'cover',
      margin: '10px',
      minHeight: '300px',
      color: 'white'}}>

      <div style={{
        position: 'absolute',
        bottom: 0,
        left: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.7)',
        padding: '10px',
        width: '100%'}}>
        <h4>{pet.name}</h4>
      </div>
    </div>

export default PetsCell;
